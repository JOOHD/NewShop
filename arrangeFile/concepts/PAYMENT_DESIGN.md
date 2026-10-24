# 결제 설계 — 겪은 문제, 보완, 한계

> JooShop 결제 로직을 점검하면서 발견한 문제와 보완 방법을 한곳에 모은 기록입니다.
> 다음에 결제를 구현할 때 **"무엇을 놓치기 쉬운가"를 복기하는 체크리스트**로 쓰기 위해 정리했습니다.
> (코드는 이 프로젝트의 실제 구현이며, 실제 PG 테스트 결제로 확인하지 못한 항목은 "미검증"으로 표시했습니다.)

---

## 0. 한눈에 보기

| # | 문제 | 위험 | 보완 | 위치 |
|---|---|---|---|---|
| 1 | 화면이 서버 주문(DB에 저장된 주문) 없이 결제를 호출 | 서버에는 주문이 없는데 돈만 결제됨 | 결제 버튼 → 서버 주문 생성(`/order/confirm`) → 그 값으로 결제 | `order.html`, `OrderApiController` |
| 2 | 결제 금액을 화면 값과 비교 | 100원 결제 후 10만 원 주문 완료 | **DB의 결제 금액**과 PG가 확인한 금액 비교 | `PaymentService` |
| 3 | 금액이 달라 거부해도 돈은 이미 승인됨 | 고객 돈만 빠져나감 | 불일치 시 승인된 결제를 **자동 취소** | `PaymentController` |
| 4 | 결제 취소에 주인 확인 없음 | 남의 결제를 번호만 바꿔 취소 | 본인(또는 관리자)만 취소 | `PaymentService.cancelPayment` |
| 5 | 결제 완료 처리에 주문 주인 확인 없음 | 남의 주문을 내 결제로 완료 처리 | 요청 회원 = 주문 회원 확인 | `PaymentService` |
| 6 | 같은 결제 검증이 두 번 처리됨(더블클릭·재시도) | 결제 이력 중복, 이중 후처리 | `imp_uid` 중복 확인(멱등성) | `PaymentService`, `PaymentRepository` |
| 7 | 동시에 오는 두 요청이 중복 확인을 함께 통과 | 위 확인이 뚫림 (조회와 저장 사이의 틈) | 주문 행 **비관적 락** | `OrderRepository.findByIdForUpdate` |
| 8 | 주문이 만들어질 때 이미 `COMPLETE` | 결제 안 해도 완료된 주문 | 주문은 `PENDING`으로 시작, 검증 후 `COMPLETE` | `Orders` |
| 9 | 결제 안 한 주문이 재고를 계속 잡음 | 재고가 풀리지 않음 | 만료 배치가 주문 취소 + 재고 복구 | `OrderExpirationScheduler` |
| 10 | 이미 결제된 주문에 또 결제, 만료된 주문에 늦은 결제 | 이중 결제 / 주문 없는 결제 | 결제 대기 주문만 허용 + 승인된 결제 자동 취소 | `OrderNotPayableException` |
| 11 | 주문번호를 화면이 만듦 | 결제와 주문을 묶을 수 없음, 남의 결제 도용 | 서버 발급 주문번호 + 결제의 주문번호 비교 | `OrderService`, `PaymentService` |
| 12 | 배송비를 화면에서만 계산 | 서버 금액과 화면 금액 불일치 | 서버가 배송비를 계산해 결제 금액에 포함 | `Orders.getPayAmount()` |
| 13 | 결제 후 이동할 페이지가 없음 | 결제 직후 404 | `/order-complete` 추가 | `OrderViewController` |

> 핵심 원칙: **금액·주문·소유자는 항상 서버가 DB 기준으로 판단한다. 화면과 요청 값은 입력일 뿐 근거가 아니다.**

---

## 1. 흐름 비교

### 수정 전 (실제로는 연결되지 않은 흐름)

```
cart.html : 화면의 글자(DOM)에서 가격을 읽어 orderDto를 만들고 sessionStorage에 저장
tempOrder → order : 같은 값을 그대로 전달
order.html: IMP.request_pay(amount = 화면 totalPrice, merchant_uid = "order_" + 현재시각)
            → POST /api/v1/payment/{imp_uid}  (orderId는 화면에 없어 undefined)
서버      : 주문이 만들어진 적이 없음 (/order/confirm을 호출하는 곳이 없었음)
            금액 검증은 "PG 금액 vs 화면이 보낸 금액"
```

### 수정 후

```
[화면] 결제 버튼 → POST /api/v1/order/confirm
[서버] 장바구니 검증 → 재고 차감(조건부 UPDATE) → 주문 저장(PENDING, merchantUid 서버 발급, 배송비 계산)
       응답: { orderId, merchantUid, amount, orderName }
[화면] 응답 값으로 결제창 호출 (merchant_uid, amount 모두 서버 값)
[PG]   결제 승인
[화면] POST /api/v1/payment/{imp_uid}
[서버] ① 로그인 사용자 확인 ② 주문 행 잠금 ③ 주문 주인 확인 ④ 주문번호 일치
       ⑤ imp_uid 중복(멱등) ⑥ 주문 상태 = PENDING ⑦ PG 금액 = DB 결제 금액
       → 주문 COMPLETE + 결제 이력 저장
       ⑥⑦ 실패 시: 승인된 결제를 자동 취소
[배치] 30분 지나도 PENDING인 주문 → 취소 + 재고 복구
```

---

## 2. 문제별 상세

### 문제 1. 화면이 서버 주문 없이 결제를 호출

> **서버 주문** = 서버(DB)에 실제로 저장된 주문(`Orders` 행). 반대로 화면에만 있는 주문은 브라우저 `sessionStorage`의 `orderDto`이고, 사용자가 값을 바꿀 수 있어 믿을 수 없다.

**증상** 결제 화면(`order.html`)이 `orderDto.orderId`를 보내는데, 장바구니 화면이 만든 `orderDto`에는 `orderId`가 없었다. `/api/v1/order/confirm`(주문 생성)을 호출하는 곳도 없었다. 서버 쪽은 Redis 임시 주문을 걷어내는 리팩토링을 했는데 화면이 옛 흐름 그대로였다.

**보완** 결제 버튼을 누르면 먼저 서버에 주문을 만들고, 응답의 주문번호·금액으로 결제창을 연다.

```javascript
// order.html — 서버 주문을 먼저 만들고, 그 값으로 결제
serverOrder = await createServerOrder(orderDto);      // POST /api/v1/order/confirm
IMP.request_pay({
    merchant_uid: serverOrder.merchantUid,            // 서버가 발급한 주문번호
    amount: serverOrder.amount,                        // 서버가 계산한 결제 금액
    ...
}, callback);
```

> 러닝포인트: 백엔드를 리팩토링할 때는 그 API를 부르는 화면과 흐름도 같이 확인한다. 서버 테스트가 통과해도 화면 연결은 검증되지 않는다.

### 문제 2. 결제 금액을 화면 값과 비교

**증상** 컨트롤러가 "PG가 확인한 금액"과 "클라이언트가 보낸 금액"만 비교했다. 둘 다 사용자가 맞춰 보낼 수 있다.

```java
// 수정 전 — 기준이 클라이언트 값
if (iamportAmount.compareTo(requestAmount) != 0) { ... }
```

**공격 예** 개발자도구에서 `orderDto.totalPrice`를 100으로 바꾸고 결제하면 PG는 100원을 승인하고, 요청 금액도 100이라 검증을 통과한다. 10만 원짜리 주문이 완료 처리된다.

**보완** 기준을 **DB에 저장된 결제 금액**으로 바꿨다.

```java
if (order.getPayAmount().compareTo(response.getAmount()) != 0) {
    throw new PaymentAmountMismatchException("결제 금액이 주문 금액과 일치하지 않습니다.");
}
```

### 문제 3. 거부해도 돈은 이미 승인됨

**증상** 금액이 달라 예외를 던져도 PG에서는 이미 출금이 끝난 상태다. 예외만 던지면 고객 돈만 빠져나가고 주문은 없다.

**보완** 불일치를 구분하는 예외를 던지고, 컨트롤러가 승인된 결제를 즉시 전액 취소한다.

```java
try {
    paymentService.processPaymentDone(iamportPayment, request);
} catch (PaymentAmountMismatchException | OrderNotPayableException e) {
    cancelMismatchedPayment(impUid);     // iamportClient.cancelPaymentByImpUid(new CancelData(impUid, true))
    throw e;
}
```

> 주의: **이 결제가 이 주문의 것임이 확인된 경우에만** 자동 취소한다. 주문번호가 다른 결제를 취소하면 남의 정상 결제를 환불시키게 된다(문제 11).

### 문제 4. 결제 취소에 주인 확인이 없음

**증상** `cancelPayment`가 결제 내역 번호로 내역을 찾아 바로 취소했다. 로그인한 아무나 번호만 바꾸면 다른 사람의 결제를 취소할 수 있었다(IDOR).

```java
// 보완
PaymentHistory paymentHistory = paymentRepository.findById(paymentHistoryId)
        .orElseThrow(() -> new PaymentHistoryNotFoundException("결제 내역을 찾을 수 없습니다."));
verifyUserIdMatch(paymentHistory.getMember().getId());   // 본인 또는 관리자만
```

### 문제 5. 결제 완료 처리에 주문 주인 확인이 없음

**증상** 로그인 사용자와 요청의 `memberId`가 같은지만 확인했다. 그 주문이 정말 이 사용자의 것인지는 확인하지 않았다.

```java
if (!order.getMember().getId().equals(request.getMemberId())) {
    throw new SecurityException("본인의 주문이 아닙니다.");
}
```

### 문제 6. 같은 결제 검증이 두 번 처리됨 — 멱등성

**증상** 더블클릭, 네트워크 재시도로 같은 `imp_uid`가 두 번 오면 결제 이력이 중복 저장된다.

**보완** 저장하기 전에 이미 처리된 결제번호인지 확인하고, 있으면 다시 처리하지 않는다.

```java
if (paymentRepository.existsByImpUid(response.getImpUid())) {
    log.info("이미 처리된 결제입니다 — impUid: {}", response.getImpUid());
    return;
}
```

> 멱등성: 같은 요청을 여러 번 보내도 결과가 한 번 보낸 것과 같은 성질. (BACKEND_CORE 95번)

### 문제 7. 동시 요청이 중복 확인을 함께 통과

**증상** 위 확인은 "조회 → 저장" 두 단계라, 두 요청이 거의 동시에 오면 둘 다 "없음"을 보고 저장한다.

```
요청 1: ① 조회 → 없음 ──────────► ② 저장
요청 2:     ① 조회 → 없음(아직 저장 전) ─► ② 저장      → 중복 저장
```

**보완** 같은 주문에 대한 처리를 주문 행 잠금으로 직렬화한다. 두 번째 요청은 첫 번째가 끝날 때까지 기다리고, 그때는 이미 저장되어 있다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select o from Orders o where o.orderId = :orderId")
Optional<Orders> findByIdForUpdate(@Param("orderId") Long orderId);

// processPaymentDone의 시작
Orders order = orderRepository.findByIdForUpdate(request.getOrderId()).orElseThrow(...);
```

**검증** `PaymentDuplicateConcurrencyTest` — 5개 스레드가 같은 `imp_uid`로 동시에 처리한 결과 (실측)

| 방식 | 저장된 결제 이력 |
|---|---|
| 조회 후 저장만 (잠금 없음) | **5건** — 중복 확인이 뚫림 |
| 주문 행 잠금 사용 | **1건** |

### 문제 8. 주문이 결제 전에 이미 COMPLETE

**증상** `Orders` 생성자가 `paymentStatus = COMPLETE`로 시작했다. 결제를 하지 않아도 완료된 주문처럼 남았다.

```java
// 수정 후 — 결제 대기로 시작
this.paymentStatus = PaymentStatus.PENDING;
// 결제 검증이 끝나야 COMPLETE (processPaymentDone: order.changePaymentStatus(PaymentStatus.COMPLETE))
```

### 문제 9. 결제 안 한 주문이 재고를 계속 잡음

**증상** 재고는 주문을 만들 때 차감하므로, 결제창을 닫거나 이탈한 주문이 재고를 계속 잡는다.

**보완** 일정 시간(기본 30분) 지난 `PENDING` 주문을 주기적으로 취소하고 재고를 복구한다.

```java
@Scheduled(fixedDelayString = "${order.pending-expire.check-interval-ms:300000}")
public void expirePendingOrders() {
    LocalDateTime before = LocalDateTime.now().minusMinutes(expireMinutes);
    for (Long id : orderRepository.findOrderIdsByStatusAndCreatedBefore(PaymentStatus.PENDING, before)) {
        try { orderExpirationService.expire(id); } catch (Exception e) { log.error(...); }
    }
}

// OrderExpirationService.expire — 주문 1건당 별도 트랜잭션
Orders order = orderRepository.findByIdForUpdate(orderId).orElse(null);
if (order == null || order.getPaymentStatus() != PaymentStatus.PENDING) return false;  // 잠근 뒤 다시 확인
order.markCanceled();
qtyByVariant.forEach(productVariantRepository::increaseStock);      // 옵션 ID 오름차순, 같은 옵션은 합산
```

- 결제 검증과 **같은 주문 행 잠금**을 써서 "만료 처리"와 "결제 완료"가 동시에 와도 한쪽만 처리된다.
- 스케줄러와 처리 서비스를 **클래스로 분리**했다. 같은 클래스 안에서 호출하면 `@Transactional`이 적용되지 않는다(self-invocation).
- 재고 복구도 조건부 UPDATE 한 문장(`stock = stock + :qty`)이라 동시 요청과 섞여도 값이 틀어지지 않는다.

### 문제 10. 결제할 수 없는 주문에 결제가 들어옴

**상황** (a) 이미 결제 완료된 주문에 다른 결제번호로 두 번째 결제 (b) 만료·취소된 주문에 뒤늦게 결제.

```java
if (order.getPaymentStatus() != PaymentStatus.PENDING) {
    throw new OrderNotPayableException("결제할 수 없는 주문입니다. (상태: " + order.getPaymentStatus() + ")");
}
```

이 예외도 승인된 결제를 자동 취소한다(문제 3). 같은 `imp_uid`의 재요청은 앞의 멱등 확인(문제 6)에서 먼저 정상 처리된다.

### 문제 11. 주문번호를 화면이 만듦

**증상** 결제창에 넘기는 `merchant_uid`가 `order_${Date.now()}`로 화면에서 만들어져서, 서버의 주문번호와 달랐다. 결제와 주문을 연결할 근거가 없었다.

**보완** 주문번호는 항상 서버가 발급하고(클라이언트 값 무시), 결제 검증에서 PG의 주문번호와 비교한다.

```java
// OrderService — 클라이언트가 보낸 merchantUid는 쓰지 않는다
private String generateMerchantUid(OrderDto orderDto) {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + "-" + UUID.randomUUID().toString().substring(0, 8);
}

// PaymentService — 주문번호가 다르면 거부 (이 경우는 자동 취소하지 않음)
if (!order.getMerchantUid().equals(response.getMerchantUid())) {
    throw new IllegalArgumentException("결제의 주문번호가 이 주문과 일치하지 않습니다.");
}
```

주문번호가 다른 결제를 자동 취소하지 않는 이유: 공격자가 **다른 사람의 `imp_uid`**를 자기 주문에 붙여 보내면, 금액 불일치로 그 사람의 정상 결제가 환불될 수 있다. 취소는 "이 결제가 이 주문의 것"임이 확인된 뒤에만 한다.

### 문제 12. 배송비를 화면에서만 계산

**증상** 화면은 5만 원 미만이면 배송비 5,000원을 더했지만 서버의 `Orders.totalPrice`는 상품 합계뿐이었다. 서버가 DB 금액으로 검증하는 순간 배송비가 있는 모든 정상 결제가 거부되는 구조였다. (또 `tempOrder.html`은 이미 배송비가 포함된 합계에 배송비를 한 번 더 더해 이중으로 표시했다.)

**보완** 서버가 배송비를 계산해서 결제 금액에 포함한다.

```java
// Orders
@Column(name = "delivery_fee", nullable = false, columnDefinition = "decimal(19,2) default 0")
private BigDecimal deliveryFee = BigDecimal.ZERO;

public BigDecimal getPayAmount() { return this.totalPrice.add(this.deliveryFee); }   // 결제 검증 기준

// 주문 상품이 추가될 때마다 재계산
this.deliveryFee = this.totalPrice.compareTo(FREE_DELIVERY_THRESHOLD) >= 0 ? BigDecimal.ZERO : DEFAULT_DELIVERY_FEE;
```

- 결제 이력의 금액(취소 시 환불 금액)도 `getPayAmount()`로 저장한다.
- 기존 주문은 컬럼 기본값 0으로 채워져 금액이 바뀌지 않는다.

### 문제 13. 결제 후 이동할 페이지가 없음

결제 성공 후 `/order-complete`로 이동하지만 이 경로를 처리하는 컨트롤러와 화면이 없어 404였다. `OrderViewController`와 `orderComplete.html`을 추가했다.

---

## 3. 결제 구현 체크리스트 (다음 프로젝트에서)

**금액·주문**
- [ ] 금액은 서버가 DB 기준으로 계산하고, PG가 확인한 금액과 비교한다 (화면·요청 값은 비교 기준이 아님)
- [ ] 주문번호(merchant_uid)는 서버가 발급하고, 결제 검증에서 PG의 값과 비교한다
- [ ] 주문을 먼저 만들고(`PENDING`), 결제 검증이 끝나면 완료로 바꾼다
- [ ] 배송비·할인 같은 금액 규칙은 서버에 있다

**소유자·권한**
- [ ] 결제 조회·취소·완료 처리 모두 "이 리소스가 로그인한 사용자의 것인가"를 확인한다 (URL 번호만 바꿔도 되는지 항상 의심)

**중복·동시성**
- [ ] 같은 결제번호가 두 번 와도 한 번만 처리된다 (멱등성)
- [ ] 마지막 방어선은 DB 유니크 제약이다. 불가능하면 행 잠금으로 직렬화한다
- [ ] 같은 주문에 두 번째 결제(다른 결제번호)가 오면 어떻게 할지 정했다

**실패·보상**
- [ ] 검증 실패로 거부할 때 이미 승인된 결제를 어떻게 되돌릴지 정했다 (자동 취소)
- [ ] 자동 취소는 "이 결제가 이 주문의 것"이 확인된 경우에만 한다
- [ ] 외부(PG) 호출이 성공한 뒤에 DB를 바꾼다 (취소 흐름: API 성공 확인 후 DB 변경)

**재고·상태**
- [ ] 결제 전 주문이 재고를 잡는다면 만료·복구 장치가 있다
- [ ] 만료 처리와 결제 완료가 동시에 와도 한쪽만 처리된다 (같은 잠금)
- [ ] 주문 상태 전이 규칙(`PENDING → COMPLETE → CANCELED`)이 코드에 있다

**운영**
- [ ] PG 웹훅으로 브라우저 이탈에도 결제 완료를 처리한다
- [ ] 자동 취소 실패 같은 "사람이 확인해야 하는 상황"은 로그로 남긴다
- [ ] 실제(테스트) 결제로 한 번 끝까지 확인했다

---

## 4. 현재 JooShop의 한계와 리팩토링 방향

> 이 프로젝트는 위 문제들을 보완했지만, 실무 결제 구조와는 아래 차이가 남아 있습니다.

### 한계

| 한계 | 설명 |
|---|---|
| **테스트 결제로 끝까지 검증하지 못함** (미검증) | 단위 테스트와 동시성 테스트는 있지만, 화면 → 서버 주문 → PG → 검증 전체 흐름은 PG 테스트 결제로 확인이 필요하다. 자동 취소 동작도 마찬가지 |
| **`imp_uid`에 DB 유니크 제약이 없음** | 주문 상품마다 결제 이력 행이 생겨 같은 `imp_uid`가 여러 행에 들어간다. 지금은 확인 조회 + 주문 행 잠금으로 보완 (애플리케이션 수준의 방어) |
| **웹훅 없음** | 결제 후 브라우저를 닫으면 서버에 결제 완료가 전달되지 않는다. 주문은 만료되고, PG에는 결제가 남을 수 있다 |
| **PG 결제 상태 확인 없음** | 금액·주문번호는 확인하지만 결제 상태가 `paid`인지는 확인하지 않는다 |
| **장바구니 소유자 확인 부족** | `confirmOrder`가 요청 `memberId`와 로그인만 비교하고, 가져온 장바구니 항목이 그 회원의 것인지는 확인하지 않는다 (다른 사람의 장바구니 ID로 주문 가능성) |
| **결제 후 장바구니가 비워지지 않음** | 결제 완료 후 사용한 장바구니 항목을 정리하는 로직이 없다 |
| **결제 대기 중 재고가 묶임** | 만료(30분)까지 재고가 잡힌다. 결제 시점에 차감하는 방식·재고 예약 모델과 비교 필요 |
| **환불 시 재고 복구 없음** | 결제 취소·환불 후 재고를 되돌리지 않는다 |
| **부분 취소 불가** | 취소는 전액만. 결제 이력 행마다 주문 전체 금액을 갖는 구조라 상품 단위 취소 의미가 모호하다 |
| **결제 수단·PG 설정이 코드에 고정** | `pg: html5_inicis`, 가맹점 식별코드가 화면에 하드코딩 |
| **결제 상태 전이 규칙이 느슨함** | `changePaymentStatus`가 어떤 상태로든 바꾼다 |

### 리팩토링 방향 (우선순위)

1. **장바구니 소유자 확인** — 작음, 보안상 가장 급함. `cart.getMember().getId()`가 요청 회원과 같은지 확인
2. **테스트 결제로 흐름 검증** — 화면 → 주문 → 결제 → 완료, 금액 불일치 자동 취소, 만료 후 결제
3. **PG 결제 상태(`paid`) 확인과 주문 상태 전이 규칙** — 작음~중간
4. **결제 후 장바구니 정리, 환불 시 재고 복구** — 작음~중간
5. **결제 테이블 재설계** — 큼. 결제 1건 = 1행(`imp_uid` UNIQUE) + 결제 상품은 별도. 중복 방어를 DB 제약으로 완성하고 부분 취소를 지원

```java
// 5번 방향 — 결제 1건당 1행, imp_uid UNIQUE
@Table(name = "payment", uniqueConstraints = @UniqueConstraint(columnNames = "imp_uid"))
class Payment {
    private String impUid;                         // UNIQUE: 같은 결제가 두 번 저장될 수 없다
    @OneToOne private Orders order;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING) private PaymentStatus status;   // READY, PAID, CANCELED, FAILED
}
// 중복은 DB가 거부하고, 애플리케이션은 이를 "이미 처리됨"으로 응답
try { paymentRepository.save(Payment.paid(order, impUid, amount)); }
catch (DataIntegrityViolationException e) { return; }
```

6. **웹훅** — 중간~큼. PG가 서버로 직접 결제 결과를 통지. 콜백과 웹훅이 같은 검증 메서드를 호출해 어느 쪽이 먼저 와도 같은 결과가 되게 한다
7. **구성 외부화** — PG·가맹점 설정을 환경 변수와 서버 응답으로

### 실무형 구조와의 비교

| 항목 | JooShop (현재) | 실무에서 많이 쓰는 구조 |
|---|---|---|
| 주문번호 | 서버 발급, 검증 시 비교 | 동일 |
| 금액 기준 | DB 결제 금액 | 동일 |
| 결제 통지 | 브라우저 콜백만 | 콜백 + **웹훅** |
| 결제 데이터 | 주문상품마다 이력 행 | 결제 1건 = 1행, `imp_uid` UNIQUE |
| 중복 방어 | 확인 조회 + 행 잠금 | UNIQUE 제약 + 멱등 처리 |
| 미결제 주문 | 만료 배치(30분) | 만료 배치 또는 재고 예약 |
| 상태 관리 | 단순 값 변경 | 허용된 전이만 가능한 상태 머신 |
| 환불 | 전액 | 부분 취소·보상 처리 |

---

## 5. 관련 테스트

| 테스트 | 확인하는 것 |
|---|---|
| `PaymentServiceTest` | 정상 결제, 남의 주문/결제 거부, 금액 불일치(100원 vs 5만 원), 주문번호 불일치, 이미 결제된 주문에 재결제, 중복 `imp_uid`, 배송비 포함 금액 |
| `PaymentDuplicateConcurrencyTest` | 같은 `imp_uid` 동시 처리 시 잠금 사용 1건 / 미사용 비교 |
| `OrderExpirationServiceTest` | `PENDING` 주문 취소 + 같은 옵션 수량 합산 재고 복구, 이미 결제된 주문은 건드리지 않음 |
| `StockConcurrencyTest` | 재고 10개에 50명 동시 주문 → 10건 성공 (조건부 UPDATE) |

관련 개념: BACKEND_CORE 79번(조건부 UPDATE·락), 91번(조건부 UPDATE), 95번(멱등성), 96번(행 잠금), 66번(self-invocation)
