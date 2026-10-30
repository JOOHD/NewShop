# 결제 설계 — 겪은 문제, 보완, 한계

> **구성**: 0~2 겪은 문제와 보완(21건) → 3 구현 체크리스트 → 4 현재 한계 → 5 관련 테스트 → **6 구조 변경 요약(2026-10)** → **7 앞으로의 계획** → **8 최종 목표 구조·설계·코드 원칙**
>
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
| 14 | 장바구니 화면이 `item.id`를 읽음 (서버 응답은 `cartId`) | 주문 생성 시 "장바구니 항목이 없다" 오류, 수량 변경·삭제 오작동 | `item.cartId`로 수정 + 필드 이름 **계약 테스트** | `cart.html`, `FrontendApiContractTest` |
| 15 | "바로 구매"가 옛 형식 주문 데이터를 만들어 장바구니 기반 서버와 맞지 않음 | 바로 구매 결제 불가 | 장바구니 없이 `items`(옵션·수량)로 주문하는 경로 분리 | `OrderDto`, `OrderService.resolveOrderLines` |
| 16 | `cartIds`가 본인 것인지 확인하지 않음 | F12/Postman으로 남의 장바구니 ID를 넣어 주문 (IDOR) | 각 장바구니의 주인 확인 | `OrderService.verifyCartOwner` |
| 17 | 바로 구매 수량·항목 수 상한 없음 | 비정상적으로 큰 수량으로 재고 점유 | 수량 1~99, 항목 20종 상한 | `OrderItemRequest`, `OrderDto` |
| 18 | 화면은 **할인가**, 서버는 **정가**로 주문 저장 | 화면 119,625원인데 159,500원 청구, 배송비도 정가 기준 | 단가 계산을 `Product.getDiscountedPrice()` 한 곳으로 | `Product`, `OrderService`, `CartDto` |
| 19 | 결제 취소 시 클릭한 이력 한 줄만 취소, 주문은 `COMPLETE` 유지 | 상태 불일치, 같은 결제를 다시 취소 시도 | 같은 결제의 이력·결제 기록·주문을 함께 취소 | `PaymentService.cancelPayment` |
| 20 | `imp_uid`에 DB 제약 없음 (조회+잠금뿐) | 코드가 뚫리면 중복 저장 | 결제 1건 = 1행 `PaymentRecord`, `imp_uid` UNIQUE | `PaymentRecord` |
| 21 | 상태를 어떤 값으로든 변경 가능 | 취소된 주문 되살리기 등 | 허용된 전이만 가능한 상태 전이 규칙 | `PaymentStatus.canTransitionTo` |
| 22 | 화면(JS)이 금액·배송비를 따로 계산 | 서버 규칙이 바뀌면 화면과 어긋남 | 서버 금액 미리보기 API를 화면이 표시만 함 | `OrderService.previewOrder`, `cart.html` 외 |
| 23 | 결제 후 장바구니 항목이 남음 | 이미 주문한 상품이 장바구니에 계속 보임 | 결제 완료 시 본인·주문 옵션 항목만 삭제 | `PaymentService.clearPaidCarts` |
| 24 | 결제 취소 후 재고가 복구되지 않음 | 취소된 수량만큼 재고가 영구히 줄어듦 | 취소 시 옵션별 합산·ID 순 복구, 한 번만 실행 | `PaymentService.cancelPayment`, `Orders.quantityByVariant` |

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

### 문제 14. 화면과 서버의 필드 이름 불일치 (`item.id` vs `cartId`)

**증상** 장바구니에서 주문하면 서버 로그에 `주문할 장바구니 항목이 없습니다`가 났다. 서버 응답(`CartDto`)의 필드는 `cartId`인데 `cart.html`이 `item.id`를 읽어 `undefined`가 됐고, 그 값이 주문 요청의 `cartIds`로 들어갔다. 수량 변경·삭제 버튼도 같은 이유로 오작동했다.

**왜 못 잡았나** 서버 테스트는 모두 통과했다. 화면(JS)과 서버(JSON)가 맞물리는 부분은 서버 테스트가 보지 않는다.

**보완** 화면이 읽는 필드 이름과 서버가 내려주는 필드 이름을 함께 검사하는 **계약 테스트**를 추가했다.

```java
// FrontendApiContractTest
assertThat(json.has("cartId")).isTrue();                 // 서버 응답에 cartId가 있고
assertThat(json.has("id")).isFalse();
assertThat(read("carts/cart.html")).contains("item.cartId")   // 화면이 그 이름을 읽는다
                                   .doesNotContain("item.id");
```

> 러닝포인트: 서버 단위 테스트가 통과해도 화면 연결은 실제로 눌러 봐야 드러난다. 최소한 이름 계약은 테스트로 고정한다.

### 문제 15. "바로 구매"가 장바구니 기반 서버와 맞지 않음

**증상** 상품 페이지의 바로 구매는 옛 형식의 주문 데이터(`productNames[]`, `productQuantities[]`)를 만들었고 `cartIds`가 없어 서버가 주문을 만들지 못했다.

**검토한 방법** 바로 구매를 장바구니에 담은 뒤 주문하는 방법은 임시방편이다. 결제를 취소해도 장바구니에 항목이 남고, 같은 옵션이 이미 있으면 수량이 덮어써진다. 일반적인 쇼핑몰의 바로 구매는 장바구니와 무관하게 주문서로 이동한다.

**보완** 주문 요청이 두 가지 입력을 받게 했다.

```
장바구니 주문: { cartIds: [1, 2] }
바로 구매:     { items: [{ variantId: 7, quantity: 2 }] }
```

```java
private List<OrderLine> resolveOrderLines(OrderDto orderDto) {
    if (orderDto.getItems() != null && !orderDto.getItems().isEmpty()) {
        // 바로 구매: 옵션을 DB에서 읽어 (옵션, 수량)으로
        return ...;
    }
    // 장바구니 구매: cartIds로 Cart를 읽어 (옵션, 수량)으로
    return ...;
}
```

두 경로 모두 `OrderLine(옵션, 수량, cart)`이라는 같은 중간 형태로 바뀌어 재고 차감, 금액 계산, 결제 검증을 공유한다. 가격과 상품명은 어느 경로든 서버가 DB에서 읽는다.

### 문제 16. 남의 장바구니로 주문 (IDOR)

**증상** `confirmOrder`는 요청의 `memberId`가 로그인 사용자와 같은지만 확인했다. `cartIds`는 클라이언트가 보내는 값이라, 개발자도구(F12)나 Postman으로 남의 장바구니 번호를 넣어도 `memberId`가 본인 것이면 통과했다.

**보완** 장바구니에서 온 항목은 모두 요청 회원의 것인지 확인한다. 재고 차감 전에 거부한다.

```java
private void verifyCartOwner(List<OrderLine> lines, Long memberId) {
    for (OrderLine line : lines) {
        Cart cart = line.cart();
        if (cart != null && !memberId.equals(cart.getMember().getId())) {
            throw new SecurityException("본인의 장바구니가 아닙니다.");
        }
    }
}
```

> 원칙: 화면에서 숨기거나 막은 값도 서버로 가는 요청은 사용자가 바꿀 수 있다. **번호(ID)로 가져온 모든 자원은 주인을 확인한다.**

### 문제 17. 수량·항목 개수 상한

바로 구매의 `items`에 1개 이상만 검증하면 아주 큰 수량으로 재고를 한꺼번에 잡을 수 있다. 수량 1~99, 항목 최대 20종으로 제한했다 (`@Max(99)`, `@Size(max = 20)`). 화면에도 같은 상한 안내를 두었지만 서버 검증이 본체다.

### 문제 18. 화면은 할인가, 서버는 정가

**증상** 장바구니 화면은 할인가 119,625원(159,500원의 25% 할인)을 보여 주는데, 서버는 주문 상품 가격을 정가(`product.getPrice()`)로 저장했다. DB의 주문 합계가 159,500원이었고, 결제 금액 검증은 서버 기준이라 통과되어 **화면보다 39,875원 더 청구**될 구조였다. 배송비도 정가 기준이라 할인가가 5만 원 미만이어도 배송비가 붙지 않았다.

**원인** 단가 계산이 두 곳에 있었다. 화면용(`CartDto`)은 할인을 적용했고, 주문용(`OrderService`)은 적용하지 않았다.

**보완** 계산을 도메인(`Product`) 한 곳으로 모으고 양쪽이 모두 쓴다.

```java
// Product
public BigDecimal getDiscountedPrice() {
    int rate = (discountRate == null) ? 0 : discountRate;
    return price.multiply(BigDecimal.valueOf(100L - rate))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);   // 원 단위 절사
}
// CartDto.finalPrice  = product.getDiscountedPrice()
// OrderService 주문 상품 가격 = product.getDiscountedPrice()
```

> 원칙: **돈 계산은 한 곳에서만 한다.** 같은 규칙을 두 곳에서 구현하면 언젠가 어긋난다. (남은 중복은 7장의 금액 미리보기 API로 정리한다)

### 문제 19. 결제 취소 시 상태가 일부만 바뀜

**증상** Iamport 취소는 결제 전체(`imp_uid`) 단위인데, DB는 사용자가 클릭한 결제 이력 한 줄만 `CANCELED`로 바꿨다. 같은 결제의 다른 상품 줄은 취소 가능 상태로 남아 있어 다시 취소를 시도할 수 있었고, 주문도 `COMPLETE`로 남았다.

**보완** PG 취소가 성공한 뒤에 같은 결제의 모든 상품별 이력, 결제 기록(`PaymentRecord`), 주문을 함께 취소 처리한다.

```java
paymentRepository.findAllByImpUid(impUid).stream()
        .filter(h -> h != paymentHistory && h.isCancelable())
        .forEach(PaymentHistory::markCanceled);
paymentHistory.markCanceled();
paymentRecordRepository.findByImpUid(impUid).filter(r -> r.getStatus().isCancelable()).ifPresent(PaymentRecord::markCanceled);
if (order.getPaymentStatus() == PaymentStatus.COMPLETE) order.markCanceled();
```

### 문제 20. 중복 방어가 DB 제약이 아님 → `PaymentRecord`

**증상** `PaymentHistory`는 주문 상품마다 한 행이라 같은 `imp_uid`가 여러 행에 들어간다. 그래서 `imp_uid`에 UNIQUE를 걸 수 없었고, 중복 방어는 "조회 + 주문 행 잠금"뿐이었다.

**보완** 결제 한 건을 한 행으로 기록하는 `PaymentRecord`를 추가하고 `imp_uid`에 UNIQUE를 걸었다.

```java
@Table(name = "payment_record",
       uniqueConstraints = @UniqueConstraint(name = "uk_payment_record_imp_uid", columnNames = "imp_uid"))
public class PaymentRecord { ... impUid, member, orders, amount, payMethod, status, paidAt, canceledAt ... }
```

방어선이 세 겹이 됐다: ① 조회로 빠르게 건너뛰기 ② 주문 행 잠금으로 동시 요청 직렬화 ③ **UNIQUE 제약(마지막 방어선)**. `PaymentHistory`는 상품별 구매 내역·리뷰용으로 그대로 둔다.

- 한계: UNIQUE 위반이 나면 해당 트랜잭션 전체가 롤백된다(오류로 응답). 이 경우는 ①②를 이미 뚫은 비정상 상황이라 데이터 무결성을 우선했다.

### 문제 21. 상태 전이 규칙 없음

`changePaymentStatus`가 어떤 상태로든 바꿀 수 있어 취소된 주문을 되살리는 등의 실수를 코드가 막지 못했다.

```java
public boolean canTransitionTo(PaymentStatus next) {
    return switch (this) {
        case PENDING  -> next == COMPLETE || next == CANCELED;
        case COMPLETE -> next == CANCELED;
        case CANCELED -> false;
    };
}
// Orders.changePaymentStatus: 허용되지 않으면 IllegalStateException
```

---

## 3. 결제 구현 체크리스트 (다음 프로젝트에서)

**금액·주문**
- [ ] 금액은 서버가 DB 기준으로 계산하고, PG가 확인한 금액과 비교한다 (화면·요청 값은 비교 기준이 아님)
- [ ] 주문번호(merchant_uid)는 서버가 발급하고, 결제 검증에서 PG의 값과 비교한다
- [ ] 주문을 먼저 만들고(`PENDING`), 결제 검증이 끝나면 완료로 바꾼다
- [ ] 배송비·할인 같은 금액 규칙은 서버에 있고, **계산하는 코드가 한 곳**이다 (화면용·주문용으로 따로 만들지 않는다)
- [ ] 화면(JS)과 서버(JSON)가 맞물리는 필드 이름은 계약 테스트로 고정한다
- [ ] 장바구니 주문과 바로 구매가 같은 주문 생성 로직을 공유한다 (경로만 다르고 검증·계산은 하나)

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
| **PG 결제 승인 이후 구간은 실제 결제로 확인하지 못함** (부분 검증) | 주문 생성, 금액(할인가·배송비), 결제창 금액 일치, `PENDING` 유지, 만료 배치·재고 복구는 직접 실행해 확인했다(5-1). 그러나 PG가 승인한 뒤의 구간(`/order-complete` 이동, `COMPLETE` 전환, `payment_record` 저장, PG 자동 취소, 결제 후 취소)은 **자동 테스트로만** 확인했다. 이니시스 테스트 결제창이 카드 정보 입력을 요구해, 입력 규칙을 확인하기 전에는 결제를 진행하지 않기로 했다 |
| ~~`imp_uid`에 DB 유니크 제약이 없음~~ **(보완됨)** | `PaymentRecord`(결제 1건 = 1행, `imp_uid` UNIQUE)를 추가했다. 조회 + 주문 행 잠금이 뚫려도 DB가 마지막으로 막는다. 단 `PaymentHistory`(상품별 이력)에는 여전히 UNIQUE가 없고, 이 구조 이전에 만들어진 결제는 `PaymentRecord`가 없다 |
| **웹훅 없음** | 결제 후 브라우저를 닫으면 서버에 결제 완료가 전달되지 않는다. 주문은 만료되고, PG에는 결제가 남을 수 있다 |
| **PG 결제 상태 확인 없음** | 금액·주문번호는 확인하지만 결제 상태가 `paid`인지는 확인하지 않는다 |
| ~~결제 후 장바구니가 비워지지 않음~~ **(보완됨)** | 결제 완료 시 본인 장바구니 중 이 주문에 담긴 옵션의 항목만 삭제한다(`PaymentService.clearPaidCarts`). 화면이 보낸 `cartIds`는 믿지 않고 소유자·주문 옵션을 다시 확인한다. 바로 구매는 해당 없음 |
| **결제 대기 중 재고가 묶임** | 만료(30분)까지 재고가 잡힌다. 결제 시점에 차감하는 방식·재고 예약 모델과 비교 필요 |
| ~~환불 시 재고 복구 없음~~ **(보완됨, 정책 기본안)** | 결제 취소 = 출고 전 취소로 보고 재고를 복구한다. 주문이 `COMPLETE → CANCELED`로 바뀌는 한 번만 실행되어 이중 복구가 없다. 실제 반품(출고 후)은 검수 뒤 복구해야 하므로 별도 정책이 필요하다 |
| **부분 취소 불가** | 취소는 전액만. 취소 시 같은 결제의 상품별 이력, `PaymentRecord`, 주문 상태가 함께 `CANCELED`가 된다(예전에는 클릭한 이력 한 줄만 취소되고 주문은 `COMPLETE`로 남았다). 상품 단위 부분 취소는 아직 없다 |
| ~~결제 금액 계산이 화면과 서버에 나뉘어 있음~~ **(보완됨)** | 서버의 금액 미리보기 API(`POST /api/v1/order/preview`)를 장바구니, 바로 구매, 주문서 화면이 그대로 표시한다. 화면에 무료배송 기준·배송비 계산이 남아 있지 않다(`FrontendApiContractTest`가 확인). 미리보기 금액 = 실제 주문 결제 금액임을 `OrderServiceTest`가 확인 |
| **결제 수단·PG 설정이 코드에 고정** | `pg: html5_inicis`, 가맹점 식별코드가 화면에 하드코딩 |
| ~~결제 상태 전이 규칙이 느슨함~~ **(보완됨)** | `PaymentStatus.canTransitionTo`로 허용된 전이(PENDING→COMPLETE/CANCELED, COMPLETE→CANCELED)만 가능. 취소된 주문을 되살리거나 같은 상태로 바꾸면 예외 |

### 리팩토링 방향

> 우선순위와 단계별 계획은 **7장**에 정리했다. 아래는 초기에 적어 둔 방향이며, 일부는 이미 반영됐다
> (장바구니 소유자 확인, 주문 상태 전이 규칙, `PaymentRecord` 도입).

```java
// 결제 테이블 방향 — 결제 1건당 1행, imp_uid UNIQUE (현재 PaymentRecord로 1차 구현됨)
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
| `OrderServiceTest` | 장바구니 주문, 바로 구매(장바구니 미조회·서버 가격), 남의 장바구니 거부, 재고 부족, 빈 장바구니 |
| `OrderDtoValidationTest` | 바로 구매 수량 1~99, 항목 20종 상한 |
| `FrontendApiContractTest` | 화면과 서버가 약속한 필드 이름(`cartId`, `orderId`, `merchantUid`, `amount`, `items`, `cartIds`) |
| `ProductDiscountedPriceTest` | 할인가 계산 (159,500원 25% 할인 → 119,625원, 원 단위 절사) |
| `PaymentRecordUniqueTest` | 같은 `imp_uid`를 두 번 저장하면 DB가 거부 |
| `OrderStatusTransitionTest` | 주문 상태 전이 규칙 (허용/거부) |
| `PaymentServiceTest`(취소) | 결제 취소 시 같은 결제의 이력·결제 기록·주문이 함께 취소 |

관련 개념: BACKEND_CORE 79번(조건부 UPDATE·락), 91번(조건부 UPDATE), 95번(멱등성), 96번(행 잠금), 66번(self-invocation)

### 5-1. 실제 환경 검증 기록 (2026-10-26, 로컬 서버 + MySQL + 포트원 테스트 채널)

**환경**: 포트원 V1 연동, PG `html5_inicis`, 상점 ID `INIpayTest`(이니시스 테스트용), 연동 모드 테스트.

| # | 시나리오 | 결과 | 근거 |
|---|---|---|---|
| 2 | 저가 상품(5만 원 미만) | **통과** | 15,770원 주문 → `delivery_fee` 5,000원, 결제창 금액 = 상품가 + 배송비 |
| 3 | 할인 상품 금액 | **통과** | 정가 19,000원 × 17% 할인 = 15,770원. 장바구니 · 결제창 · DB(`total_price`, `price_at_order`)가 모두 일치 (수정 전에는 DB가 정가) |
| 4 | 바로 구매 | **통과** | 장바구니를 거치지 않고 주문서로 이동 |
| 5 | 결제창 닫기 | **통과** | 주문이 `PENDING`으로 남고 재고 차감 |
| 6 | 더블클릭 | 자동 테스트로 대체 | 결제 버튼을 누르는 즉시 비활성화(화면) + `PaymentDuplicateConcurrencyTest`, `PaymentRecordUniqueTest`(서버) |
| 8 | 만료 배치 + 재고 복구 | **통과** | 로그 `결제 대기 주문 1건을 만료 처리했습니다 (기준: 30분 경과)`, 주문 `CANCELED`, 재고 `product_stock`이 최초 재고 20으로 복구 |
| 1 | 결제 완료 (`COMPLETE`, `/order-complete`, `payment_record`) | **미실행** | 테스트 결제창이 카드 정보 입력을 요구해 입력 규칙 확인 전까지 보류. `PaymentServiceTest`, `PaymentRecordUniqueTest`로 대체 확인 |
| 7 | 결제 후 취소 | **미실행** | 1번이 선행되어야 함. `PaymentServiceTest`의 "취소 시 이력·기록·주문 함께 취소"로 대체 확인 |

**이번 검증에서 직접 확인한 의미**
- 화면(장바구니, 결제창)과 서버(DB) 금액이 같다 → 문제 18(할인가·정가 불일치)의 수정이 실제로 동작.
- 배송비 규칙(5만 원 미만 5,000원)이 DB와 결제창까지 일관된다.
- 결제를 하지 않은 주문이 만료 배치로 정리되고 재고가 정확히 복구된다 → 문제 9의 수정이 실제로 동작.

**검증 중 발견해 고친 것**: 문제 14(`item.id`), 15(바로 구매 형식), 18(할인가). 모두 서버 단위 테스트는 통과하던 것이 실제 화면 테스트에서 드러났다.

**남은 위험**: PG 승인 이후 구간은 실제 승인된 결제로 아직 돌려 보지 못했다. PG 응답 필드(`status`, `amount`, `merchant_uid`)가 코드의 가정과 다르면 이 구간에서 문제가 나올 수 있다. 테스트 결제창의 입력 규칙(포트원 V1 공식 문서)을 확인한 뒤 시나리오 1, 7을 재검증한다.

---

## 6. 구조 변경 요약 (2026-10)

### 6-1. 전체 비교

| 영역 | 변경 전 | 변경 후 |
|---|---|---|
| 주문 생성 | 화면이 만든 값으로 결제부터 진행, 서버 주문 없음 | 결제 전에 서버가 주문을 `PENDING`으로 만들고 번호·금액을 확정해 내려줌 |
| 주문 항목 | 장바구니만 (바로 구매는 형식이 안 맞음) | 장바구니(`cartIds`) / 바로 구매(`items`) 두 입력을 `OrderLine`으로 통일 |
| 금액 | 화면이 계산, 서버는 정가·배송비 없음 | 서버가 할인가 + 배송비로 계산(`Product.getDiscountedPrice`, `Orders.getPayAmount`) |
| 결제 검증 | PG 금액 vs 화면 금액 | 소유자 → 주문번호 → 중복 → 상태 → **DB 금액**, 불일치는 자동 취소 |
| 중복 방어 | 없음 → 조회 | 조회 + 주문 행 잠금 + `PaymentRecord`(`imp_uid` UNIQUE) |
| 결제 데이터 | 상품별 `PaymentHistory`만 | `PaymentRecord`(결제 1건) + `PaymentHistory`(상품별 내역) |
| 상태 | 값 덮어쓰기 | 허용된 전이만 가능 (`PENDING → COMPLETE → CANCELED`) |
| 취소 | 이력 한 줄만 취소 | 결제 전체(이력·기록·주문) 동기 취소 |
| 미결제 주문 | 재고를 계속 잡음 | 30분 만료 배치로 취소 + 재고 복구 |
| 권한 | 취소·장바구니 주인 확인 없음 | 결제·취소·장바구니 모두 주인 확인 |
| 화면-서버 연결 | 이름·형식 불일치(`id`/`cartId` 등) | 계약 테스트로 고정 |

### 6-2. 현재 구조 (코드 기준)

```
[화면] cart.html / productDetail.html ── cartIds 또는 items ──┐
                                                              ▼
[주문] OrderApiController.confirmOrder
        └ OrderService.confirmOrder (@Transactional)
            ① resolveOrderLines   장바구니/바로 구매 → OrderLine(옵션, 수량, cart)
            ② verifyUserIdMatch   로그인 사용자 = 요청 memberId
            ③ verifyCartOwner     장바구니 주인 확인
            ④ decreaseStocks      조건부 UPDATE (재고 >= 수량), 옵션 ID 순
            ⑤ Orders.createOrder  PENDING, merchantUid 서버 발급
            ⑥ addOrderProduct     Product.getDiscountedPrice 스냅샷 → totalPrice, deliveryFee 계산
            └ 응답: OrderConfirmResponse { orderId, merchantUid, amount(=getPayAmount), orderName }

[화면] order.html ── IMP.request_pay(서버가 준 merchantUid, amount) ── PG ── 승인
        └ POST /api/v1/payment/{imp_uid}

[결제] PaymentController → PaymentService.processPaymentDone (@Transactional)
        ① 주문 행 잠금(findByIdForUpdate)  ② 주문 주인  ③ merchantUid 일치
        ④ 중복(imp_uid)  ⑤ 상태 PENDING  ⑥ DB 금액 = PG 금액
        ⑦ Orders → COMPLETE   ⑧ PaymentRecord 저장(UNIQUE)   ⑨ PaymentHistory 상품별 저장
        └ ⑤⑥ 실패 시 PaymentController가 승인된 결제를 자동 취소

[만료] OrderExpirationScheduler → OrderExpirationService.expire (행 잠금, 취소 + 재고 복구)
[취소] PaymentService.cancelPayment → PG 취소 성공 후 이력·기록·주문 CANCELED
```

### 6-3. 추가·변경된 주요 클래스

| 클래스 | 역할 |
|---|---|
| `OrderLine` (`OrderService` 내부 record) | 장바구니와 바로 구매를 하나로 합치는 중간 형태 |
| `OrderItemRequest`, `OrderDto.items` | 바로 구매 입력 (옵션 ID + 수량, 상한 검증) |
| `OrderConfirmResponse` | 서버가 확정한 주문번호·금액을 화면에 전달 |
| `Product.getDiscountedPrice()` | 단가 계산의 단일 출처 |
| `Orders.getPayAmount()`, `deliveryFee` | 결제 금액 검증의 기준값 |
| `PaymentStatus.canTransitionTo()` | 상태 전이 규칙 |
| `PaymentRecord`, `PaymentRecordRepository` | 결제 1건 단위 기록, `imp_uid` UNIQUE |
| `OrderExpirationScheduler/Service` | 미결제 주문 만료 |
| `PaymentAmountMismatchException`, `OrderNotPayableException` | 자동 취소를 일으키는 예외 |
| `FrontendApiContractTest` 외 테스트 | 5장 참고 |

### 6-4. 배운 점 (재구조 설립의 근거)

- 오늘 나온 문제 대부분은 **구조가 틀려서가 아니라, 같은 값을 두 곳에서 따로 만들었기 때문**에 생겼다 (단가: 화면/서버, 필드 이름: JS/Java, 주문 입력: 장바구니/바로 구매, 중복 방어: 코드/DB).
- 그래서 방향은 "전부 다시 만들기"가 아니라 **"하나의 출처(single source of truth)로 모으고, 마지막 방어선은 DB와 테스트가 맡게 하기"**로 잡았다.

---

## 7. 앞으로의 계획 (우선순위순)

> 각 단계는 독립적으로 배포·검증할 수 있게 작게 나눴다. 각 단계마다 테스트를 먼저/함께 추가하고, 끝날 때 이 문서를 갱신한다.

### 단계 A. 실제 결제로 검증 (가장 먼저)

> **진행 상태 (2026-10-26)**: 결제 승인 이전 구간과 만료 배치는 직접 검증 완료(5-1). 결제 승인 이후 구간(시나리오 1, 7)은 테스트 결제창의 입력값 확인 후 재검증 예정. 아래 표는 전체 시나리오 목록이다.

지금까지는 단위·통합 테스트로만 확인했고, PG와 화면이 얽힌 전체 흐름 중 일부만 직접 검증했다.

| 시나리오 | 기대 결과 |
|---|---|
| 5만 원 이상 장바구니 주문 | 결제 → `/order-complete`, 주문 `COMPLETE`, `payment_record` 1행 |
| 5만 원 미만 주문 (임시 저가 상품 등록) | 결제 금액 = 상품가 + 배송비 5,000원 |
| 할인 상품 주문 | 화면 금액 = 결제창 금액 = DB 금액 (할인가 기준) |
| 바로 구매 | 장바구니 없이 주문서 → 결제 |
| 결제창 닫기 → 30분(테스트는 1분) 후 | 주문 `CANCELED`, 재고 복구 |
| 결제 버튼 더블클릭 | 결제창 1번, 기록 중복 없음 |
| 금액 위변조 시도 | 서버 거부 + PG 자동 취소 |
| 결제 후 취소 | 이력·기록·주문 모두 `CANCELED` |

### 단계 B. 금액 계산을 서버 한 곳으로 — **완료 (2026-10-26)**

> 구현: `POST /api/v1/order/preview`, `OrderService.previewOrder`, 화면 3곳(`cart.html`, `productDetail.html`, `tempOrder.html`)이 서버 응답만 표시. 직접 확인: 체크에 따른 금액 변동, 저가 상품 배송비, 바로 구매 주문서 이동.

- 서버에 **금액 미리보기 API**를 둔다: `POST /api/v1/order/preview` — `cartIds` 또는 `items`를 받아 상품 합계, 할인, 배송비, 결제 금액을 계산해 응답. 주문을 만들지 않고 재고도 건드리지 않는다.
- 장바구니·바로 구매·주문서 화면은 자체 계산을 지우고 이 응답을 표시한다.
- 완료 기준: 화면 어디에도 `50000`, `5000` 같은 금액 규칙 상수가 없다. 서버 응답과 DB 금액이 같다는 테스트가 있다.

### 단계 C. 주문 후 정리 — **구현 완료, 실제 결제 후 확인 대기 (2026-10-26)**

> 구현: 결제 완료 시 장바구니 정리(`clearPaidCarts`), 결제 취소 시 재고 복구(정책: 출고 전 취소로 간주). 자동 테스트로 확인. 실제 결제 완료·취소 화면 확인은 단계 A의 시나리오 1, 7과 함께 한다.

- 결제 완료 후 사용한 장바구니 항목 삭제 (바로 구매는 해당 없음).
- 환불 시 재고 복구 정책 결정 후 구현 (취소 = 미출고면 복구, 반품이면 검수 후 복구 등. 정책이 먼저다).
- 관리자 주문 화면의 상태 표기와 `CANCELED` 연동 확인.

### 단계 D. 결제 상태 확인과 웹훅 (중간~큼)

- PG 조회 결과의 상태가 `paid`인지 확인 (`paid`가 아니면 완료 처리하지 않는다).
- **웹훅**: PG가 서버로 직접 결과를 통지. 브라우저 콜백과 웹훅이 **같은 검증 메서드**를 호출해 어느 쪽이 먼저 와도, 둘 다 와도 같은 결과(멱등)가 되게 한다.
- 웹훅은 공개 엔드포인트이므로 요청을 그대로 믿지 않고 `imp_uid`로 PG를 다시 조회해서 판단한다.

### 단계 E. 결제 데이터 정리 (큼)

- `PaymentRecord`를 결제의 기준 테이블로 승격하고, 조회·취소·환불을 이쪽 기준으로 옮긴다.
- 이 구조 이전에 만든 결제(`PaymentRecord` 없음)를 이관하는 스크립트를 준비한다. 이관이 끝나면 `existsByImpUid` 이중 확인을 단순화한다.
- `PaymentHistory`는 "상품별 구매 내역(리뷰, 목록)" 역할만 하게 하고, 부분 취소는 이 단위(상품)로 설계한다.

### 단계 F. 운영 안정성 (중간)

- PG 설정(`pg`, 가맹점 식별코드)을 환경 변수와 서버 응답으로 분리 (PG 교체 시 코드 변경 최소화).
- 자동 취소 실패, 웹훅 검증 실패처럼 사람이 봐야 하는 상황을 알림/로그 지표로 남긴다.
- **정합성 점검 배치**: PG에는 결제가 있는데 DB에는 없는 건(또는 반대)을 주기적으로 찾아 알린다.
- (별개 이슈) JWT 쿠키만 있고 서버 세션이 없을 때 페이지 이동(`/tempOrder` 등)이 로그인 화면으로 가는 문제 — 웹 체인에서 JWT 인증을 어떻게 이어받을지 설계가 필요하다.

---

## 8. 최종 목표: 구조·설계·코드 원칙

> 대형 쇼핑몰의 내부 구조는 공개되어 있지 않아 그대로 따라 할 수 없다. 대신 **규모와 상관없이 결제 시스템이 공통으로 지키는 원칙**을 기준으로 삼는다.

### 8-1. 설계 원칙 7가지

1. **금액·상태·소유자의 기준은 서버(DB)** — 화면과 요청 값은 입력일 뿐이다.
2. **단일 출처** — 단가, 배송비, 상태 규칙은 도메인 객체 한 곳에 둔다 (`Product`, `Orders`, `PaymentStatus`). 서비스·화면은 그것을 부르기만 한다.
3. **주문과 결제의 분리** — 주문은 `PENDING`으로 먼저 만들고, 결제가 검증된 뒤에만 `COMPLETE`로 간다. 둘을 `merchantUid`로 묶는다.
4. **멱등성은 여러 겹** — 조회(빠른 경로) → 행 잠금(동시성) → **DB UNIQUE(마지막 방어선)**.
5. **외부(PG)와 내부(DB)의 순서** — 승인된 결제를 되돌릴 수 있는 보상 경로(자동 취소)를 항상 준비하고, PG 취소가 성공한 뒤에 DB를 바꾼다.
6. **통지는 두 경로** — 브라우저 콜백 + 웹훅이 **같은 검증 로직**을 쓴다.
7. **검증 가능해야 한다** — 규칙마다 테스트가 있고, 화면-서버 이름 계약, 동시성, 상태 전이까지 테스트로 고정한다.

### 8-2. 목표 구조

```
presentation   Controller  : 입력 검증, 인증된 사용자 전달만. 계산·판단 없음
application    OrderService / PaymentService(검증 유스케이스) / OrderExpiration
               - 트랜잭션 경계, 잠금, 순서 제어. 규칙 자체는 도메인에 위임
domain         Product(단가) / Orders(금액·상태) / PaymentStatus(전이) / PaymentRecord(결제 1건)
infrastructure Repository(조건부 UPDATE, 행 잠금), PG 클라이언트(포트원) — 교체 가능하게 얇게
```

결제 검증을 하나의 유스케이스로 모아, 콜백과 웹훅이 모두 이것을 부른다.

```java
// 목표: 콜백/웹훅 공통 진입점 (idempotent)
public PaymentResult confirm(String impUid) {
    PgPayment pg = pgClient.getPayment(impUid);                   // 항상 PG에서 다시 조회 (PgPayment = PG 응답을 감싼 단순 객체)
    if (!"paid".equals(pg.getStatus())) return PaymentResult.notPaid();

    Orders order = orders.findByMerchantUidForUpdate(pg.getMerchantUid());   // 잠금
    if (records.existsByImpUid(impUid)) return PaymentResult.alreadyDone();  // 멱등

    order.verifyPayable(pg.getAmount());          // 상태 PENDING + DB 금액 일치 (도메인이 판단)
    order.markPaid();                             // 상태 전이 규칙
    records.save(PaymentRecord.paid(order, impUid, pg.getAmount()));         // UNIQUE가 마지막 방어
    return PaymentResult.completed(order);
}   // 실패(금액/상태): 호출한 쪽이 보상(자동 취소) 실행
```

### 8-3. 코드 작성 규칙

- 컨트롤러·화면에서 금액·상태를 계산하지 않는다. 화면 표시는 서버 응답을 그대로 쓴다.
- 번호(ID)로 자원을 가져오면 **그 자리에서** 주인을 확인한다.
- 상태는 `setter`로 바꾸지 않고, 규칙이 있는 도메인 메서드(`markPaid`, `markCanceled`)로만 바꾼다.
- 외부 API 호출은 트랜잭션·잠금을 오래 잡지 않도록 순서를 설계한다 (조회 → 판단 → 변경).
- 새 결제 경로를 추가할 때는 3장 체크리스트를 먼저 훑고, 해당 항목의 테스트를 같이 추가한다.

### 8-4. 이 방향으로 가는 이유와 한계

- 얻는 것: 같은 값이 두 곳에서 어긋나는 종류의 버그가 구조적으로 줄고, 장애가 나도 원인이 한 곳으로 좁혀진다.
- 한계: 실제 대형 서비스처럼 서비스를 분리(결제/주문/재고)하거나 이벤트 기반으로 가는 것은 이 프로젝트의 규모에 맞지 않아 목표에 넣지 않았다. 단일 애플리케이션 안에서 **경계를 분명히 하는 것**까지를 목표로 한다.
