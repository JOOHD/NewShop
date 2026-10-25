# JooShop 장바구니 → 주문 → 결제 흐름 정리

> 최종 리팩토링 기준 (2026-10).
> Redis 임시주문 제거, confirmOrder 단순화, 최근 본 상품 Redis ZSET 추가.
> 2026-10: 결제 흐름 연결(서버 발급 주문번호·금액·배송비), 주문 `PENDING` 시작, 결제 검증 강화(금액·소유자·멱등·행 잠금·자동 취소), 미결제 주문 만료 배치.
> 결제 설계의 문제/보완/한계는 [`concepts/PAYMENT_DESIGN.md`](../concepts/PAYMENT_DESIGN.md) 참고.

---

## 전체 흐름 한눈에 보기

```
사용자
 ├─ 상품 상세 조회 (GET /api/v1/products/{productId})
 │       └─→ ProductServiceV1.productDetail()
 │               ├─ Redis ZSET: 전체 조회수 +1 (ProductRankingService)
 │               └─ Redis ZSET: 최근 본 상품 기록 (RecentlyViewedService) ← 로그인 시만
 │
 ├─ 장바구니 담기 (POST /api/v1/cart/add/{inventoryId})
 │       └─→ CartService.addCart()
 │               ├─ 동일 옵션 이미 있으면 수량 덮어쓰기
 │               └─ 없으면 Cart 신규 생성 → DB 저장
 │
 ├─ 화면: cart.html → tempOrder.html → order.html (sessionStorage의 orderDto는 "표시용", cartIds 포함)
 │
 ├─ 결제 버튼 → 주문 생성 (POST /api/v1/order/confirm)
 │       └─→ OrderService.confirmOrder()
 │               ├─ Cart 조회 → 재고 차감(조건부 UPDATE)
 │               ├─ Orders.createOrder() → 상태 PENDING, merchantUid 서버 발급
 │               ├─ OrderProduct 추가 → totalPrice, deliveryFee 서버 계산
 │               └─ DB 저장 (@Transactional 보호)
 │       응답: OrderConfirmResponse { orderId, merchantUid, amount(=totalPrice+deliveryFee), orderName }
 │
 ├─ 화면: IMP.request_pay(merchant_uid, amount = 서버 응답 값)
 │
 ├─ 결제 완료 알림 (POST /api/v1/payment/{imp_uid})
 │       └─→ PaymentController
 │               ├─ Iamport 서버 재조회
 │               ├─→ PaymentService.processPaymentDone()   ← 주문 행 잠금
 │               │       ├─ 주문 주인·주문번호·중복(imp_uid)·PENDING 상태·금액(DB 기준) 검증
 │               │       ├─ Order 상태 COMPLETE (Dirty Checking)
 │               │       └─ PaymentHistory INSERT
 │               └─ 금액 불일치/결제 불가 주문이면 승인된 결제를 자동 취소 후 예외
 │       성공 시 화면은 /order-complete 로 이동
 │
 ├─ (배치) 30분 지나도 PENDING인 주문 → 취소 + 재고 복구 (OrderExpirationScheduler)
 │
 └─ 결제 취소 (POST /api/v1/payment/cancel/{paymentHistoryId})
         └─→ PaymentService.cancelPayment()
                 ├─ 결제 내역 주인(본인/관리자) 확인
                 ├─ Iamport 취소 API 호출 (try-catch)
                 ├─ markCanceled() (API 성공 후에만) — 같은 결제의 상품별 이력·PaymentRecord·주문까지 함께 CANCELED
                 └─ PaymentRefund INSERT
```

---

## 1. 장바구니 (CartService)

### 흐름

```
POST /api/v1/cart/add/{inventoryId}
  │  body: { quantity: 2 }
  │  JWT 쿠키 → CustomUserDetails.getMemberId() = 42
  ▼
CartApiController.addCart(inventoryId=7, userDetails)
  - memberId = userDetails.getMemberId()  ← JWT에서 추출, body가 아님 (보안)
  ▼
CartService.addCart(memberId=42, inventoryId=7, quantity=2)
  - verifyUserIdMatch(42)  ← JWT 토큰 memberId와 일치 확인
  - findMember(42)         ← Member 조회
  - findProductManagement(7) ← ProductManagement(옵션+재고) 조회
  - cartRepository.findByMemberAndProductManagement()
      ├─ 이미 있음 → existingCart.replaceQuantity(2)  ← 수량 덮어쓰기 (Dirty Checking)
      └─ 없음 → Cart.createCart() → cartRepository.save()
```

### 메서드 로드맵

| 메서드 | 트랜잭션 | 설명 |
|---|---|---|
| `allCarts(memberId)` | `readOnly=true` | 장바구니 목록 조회 |
| `addCart(memberId, inventoryId, quantity)` | 클래스 기본 | 담기 (중복 시 수량 교체) |
| `updateCart(memberId, cartId, dto)` | 클래스 기본 | 수량 수정 (Dirty Checking) |
| `deleteCart(cartId, memberId)` | 클래스 기본 | 단건 삭제 |
| `deleteCartList(cartIds, memberId)` | 클래스 기본 | 다건 삭제 |

### 예외 발생 구간

```
addCart:
  - inventoryId 없음 → NoSuchElementException("상품 옵션을 찾을 수 없습니다")
  - 타인 memberId로 요청 → verifyUserIdMatch() 실패

updateCart / deleteCart:
  - cartId 없음 → NoSuchElementException
  - 내 장바구니가 아님 → SecurityException("권한 없음")
```

### 트랜잭션 전략

```java
@Transactional(rollbackFor = Exception.class)   // 클래스 기본
public class CartService {

    @Transactional(readOnly = true)             // 조회만 override
    public List<CartDto> allCarts() { ... }

    public Long addCart() { ... }               // 기본값 상속 (쓰기)
}
```

> **CartService에 rollbackFor = Exception.class가 남아있는 이유:**
> CartService 자체에 Checked Exception이 없어도 명시적으로 달아뒀음.
> 실용적으로는 `@Transactional`만으로 충분 — 리팩토링 대상.

---

## 2. 주문 (OrderService)

### 흐름

```
POST /api/v1/order/confirm
  │  body: { memberId:42, cartIds:[1,2,3], postCode:"12345", address:"서울시...", payMethod:"CARD" }
  ▼
OrderApiController.confirmOrder(orderDto)
  ▼
OrderService.confirmOrder(orderDto)
  @Transactional(rollbackFor = Exception.class)

  ① cartRepository.findAllById([1,2,3])
     → [Cart{상품A, qty:1}, Cart{상품B, qty:2}, Cart{상품C, qty:1}]

  ② validateCarts() → 비어있으면 IllegalArgumentException

  ③ verifyUserIdMatch(42) → JWT memberId와 일치 확인

  ④ memberAccountService.findMemberById(42) → Member 조회

  ⑤ Orders.createOrder(member, ordererName, phone, postCode, address, ..., payMethod, merchantUid)
     ← 팩토리 메서드. new Orders() 직접 생성 금지
     ← 초기 상태 PENDING (결제 검증 후 COMPLETE)
     ← merchantUid는 항상 서버가 발급 (클라이언트가 보낸 값은 무시)

  ⑥ carts.forEach(cart → order.addOrderProduct(orderProductFromCart(cart)))
     → Cart → OrderProduct 변환 (상품명, 가격, 수량 스냅샷)
     → 주문 당시 정보 고정 (이후 상품 수정/삭제돼도 이력 보존)
     → 재고는 조건부 UPDATE로 차감 (재고가 충분할 때만 성공)
     → recalculateOrderSummary(): totalPrice 합산 + deliveryFee 계산
        (5만 원 이상 무료, 미만 5,000원 / 결제 금액 = getPayAmount() = totalPrice + deliveryFee)

  ⑦ orderRepository.save(order)
     → Orders INSERT + OrderProduct INSERT (CASCADE)
     → 여기서 예외 발생 시 전체 롤백

Response: OrderConfirmResponse { orderId, merchantUid, amount, orderName }
          → 화면이 이 값으로 결제창을 연다
```

> 장바구니 소유자 확인: ③은 요청 memberId와 로그인 사용자만 비교하므로, 이어서 `verifyCartOwner`가 각 Cart의 주인이 요청 회원인지 확인한다.
> cartIds는 클라이언트가 보내는 값이라 남의 장바구니 ID를 넣을 수 있기 때문이다 (다르면 SecurityException, 재고 차감 전에 거부).
>
> **바로 구매**: 요청에 `items: [{variantId, quantity}]`가 있으면 장바구니를 거치지 않고 옵션에서 바로 주문을 만든다
> (`resolveOrderLines`가 장바구니/바로 구매를 같은 `OrderLine(옵션, 수량)`으로 바꿔 이후 로직을 공유).

### 메서드 로드맵

| 메서드 | 트랜잭션 | 설명 |
|---|---|---|
| `confirmOrder(orderDto)` | `@Transactional(rollbackFor=Exception.class)` | 유일한 메서드. Cart → DB 직접 저장 |

### 예외 발생 구간

```
confirmOrder:
  - cartIds 비어있음 → IllegalArgumentException("주문할 장바구니 항목이 없습니다")
  - 요청 memberId ≠ 로그인 사용자 → verifyUserIdMatch 실패
  - 재고 부족 → 조건부 UPDATE 실패 → 전체 롤백
  - orderRepository.save 실패 → 전체 롤백
```

### 미결제 주문 만료 (OrderExpirationScheduler / OrderExpirationService)

```
@Scheduled (기본 5분마다, order.pending-expire.check-interval-ms)
  └─ PENDING 이면서 order.pending-expire.minutes(기본 30분) 지난 주문 id 조회
       └─ 주문 1건마다 OrderExpirationService.expire(orderId)  ← 별도 트랜잭션
            ├─ findByIdForUpdate (결제 검증과 같은 행 잠금)
            ├─ 잠근 뒤 PENDING 다시 확인 (그새 결제됐으면 건너뜀)
            ├─ markCanceled()
            └─ 같은 옵션 수량을 합산해 increaseStock (옵션 id 오름차순)
```

- 스케줄러와 서비스를 클래스로 분리한 이유: 같은 클래스 내부 호출은 `@Transactional`이 적용되지 않는다(self-invocation).
- `@EnableScheduling`은 `JooshopApplication`에 선언.

### 리팩토링 포인트 (Before → After)

```
Before:
  POST /order/create  → createOrder()  → Redis 임시 저장
  GET  /order/temp    → getTemporaryOrder() → Redis 조회
  POST /order/confirm → confirmOrder() → Redis 읽기 → DB 저장

After:
  POST /order/confirm → confirmOrder() → Cart 조회 → DB 직접 저장
  이유: 일반 쇼핑몰에서 Redis 임시 주문 불필요. 클론 코딩 원본 구조 제거.
```

---

## 3. 결제 (PaymentService + PaymentController)

### 3-1. 결제 완료 처리 흐름

```
[클라이언트] Iamport 결제 완료 → 서버에 imp_uid 전달

POST /api/v1/payment/{imp_uid}
  body: { memberId:42, orderId:15, price:189000 }   ← orderId·price는 화면이 서버 주문 응답에서 가져온 값
  ▼
PaymentController.validateIamport(impUid, request)

  ① iamportClient.paymentByImpUid(impUid)
     → Iamport 서버에 직접 재조회 (서버-서버 통신)
     → 클라이언트 데이터 신뢰 안 함

  ② paymentService.processPaymentDone(iamportPayment, request)
     (금액 비교는 서비스 안에서 "DB 결제 금액" 기준으로 한다 — 요청 price와 비교하지 않음)

  ③ PaymentAmountMismatchException | OrderNotPayableException 이 나면
     → cancelMismatchedPayment(impUid): Iamport 전액 취소 (CancelData(impUid, true)) 후 예외를 다시 던짐
  ▼
PaymentService.processPaymentDone()
  @Transactional (클래스 기본값)

  ① verifyUserIdMatch(42)
  ② orderRepository.findByIdForUpdate(15)  ← 비관적 락(SELECT ... FOR UPDATE), 같은 주문의 동시 요청을 직렬화
     없으면 → OrderNotFoundException
  ③ 주문 주인 = 요청 회원 확인 (아니면 SecurityException)
  ④ order.merchantUid = 결제의 merchantUid 확인 (아니면 IllegalArgumentException, 자동 취소 안 함)
  ⑤ paymentRepository.existsByImpUid(impUid) → 이미 있으면 그대로 return (멱등)
  ⑥ order.paymentStatus == PENDING 확인 (아니면 OrderNotPayableException → 자동 취소)
  ⑦ order.getPayAmount() (= totalPrice + deliveryFee) vs 결제 금액
     다르면 PaymentAmountMismatchException → 자동 취소
  ⑧ order.changePaymentStatus(COMPLETE) ← Dirty Checking (허용된 전이만: PENDING → COMPLETE)
  ⑨ PaymentRecord 저장 (결제 1건 = 1행, imp_uid UNIQUE — 마지막 중복 방어선)
  ⑩ order.getOrderProducts() 순회 → PaymentHistory 생성 (상품별 구매 내역, 금액 = getPayAmount) → save

(중복 확인 ⑤는 PaymentRecord를 먼저, 이 구조 이전 결제는 PaymentHistory로 확인)

커밋 → Order status UPDATE + PaymentHistory INSERT
```

> 결제 후 Redis `cartIds` / `tempOrder` 정리는 더 이상 하지 않는다 (Redis 임시 주문 제거).
> 결제 후 장바구니 항목 정리는 아직 없다 (한계).

**자동 취소를 하는 경우 / 안 하는 경우**

| 상황 | 자동 취소 | 이유 |
|---|---|---|
| 금액 불일치, 결제 불가 주문(이미 완료·취소) | O | 이 결제가 이 주문의 것임이 확인된 상태 |
| 주문번호 불일치 | X | 남의 `imp_uid`를 붙여 보낸 경우 그 사람의 정상 결제를 환불시킬 수 있음 |
| 같은 `imp_uid` 재요청 | X | 멱등 처리로 정상 응답 |

### 3-2. 결제 이력 조회 흐름

```
GET /api/v1/paymentHistory/{memberId}
  ▼
PaymentService.getPaymentHistoriesByMemberId(memberId)
  @Transactional(readOnly = true)  ← 클래스 기본 @Transactional override

  - verifyUserIdMatch(memberId)
  - paymentRepository.findAllByMember_Id(memberId)
  - .map(PaymentHistoryDto::from) → DTO 변환
  - 반환

readOnly = true 효과:
  - Dirty Checking 비활성화 → PaymentHistory 수정 불가 (보호)
  - 스냅샷 생성 없음 → 성능 최적화
```

### 3-3. 결제 취소 흐름

```
POST /api/v1/payment/cancel/{paymentHistoryId}
  body: { reason:"사이즈 불량", refundHolder:"홍길동", refundBank:"국민", refundAccount:"12345" }
  ▼
PaymentService.cancelPayment(paymentHistoryId, requestDto, iamportClient)
  @Transactional (클래스 기본값)

  ① paymentRepository.findById(id)
     없으면 → PaymentHistoryNotFoundException("결제 내역을 찾을 수 없습니다")
     verifyUserIdMatch(paymentHistory.getMember().getId())  ← 본인(또는 관리자)만 취소 (IDOR 방어)

  ② paymentHistory.isCancelable()
     아니면 → IllegalStateException("이미 취소되었거나 취소할 수 없는 결제입니다")

  ③ CancelData 생성 (impUid, true, totalPrice)

  ④ try {
       cancelResponse = iamportClient.cancelPaymentByImpUid(cancelData)
     } catch (IamportResponseException | IOException e) {
       // Checked Exception → RuntimeException 변환
       // 이 시점 DB 변경 없음 → 롤백해도 안전
       throw new PaymentCancelFailureException("환불 처리 중 오류가 발생했습니다: " + e.getMessage())
     }

  ⑤ cancelResponse.getCode() != 0 → PaymentCancelFailureException("환불이 거부되었습니다")

  ⑥ paymentHistory.markCanceled()  ← API 성공 확인 후에만 DB 변경 (순서 핵심!)
     Dirty Checking → 커밋 시 UPDATE

  ⑦ PaymentRefund.createRefund() → paymentRefundRepository.save()

커밋 → PaymentHistory status UPDATE + PaymentRefund INSERT
```

### PaymentService 메서드 로드맵

| 메서드 | 트랜잭션 | 설명 |
|---|---|---|
| `processPaymentDone(response, request)` | 클래스 기본 `@Transactional` | 결제 완료 처리. 행 잠금 + 소유자·주문번호·멱등·상태·금액 검증 후 Order 상태 + PaymentHistory 저장 |
| `getPaymentHistoriesByMemberId(memberId)` | `@Transactional(readOnly=true)` override | 결제 이력 조회 |
| `cancelPayment(id, dto, client)` | 클래스 기본 `@Transactional` | 취소. Checked Exception try-catch 포장 |

### 트랜잭션 전략

```java
@Transactional           // 클래스 기본값
// Before: @Transactional(rollbackFor = Exception.class) 였음
// After:  불필요 → cancelPayment의 try-catch가 이미 Checked→Runtime 변환
//         processPaymentDone은 Checked Exception 직접 던지지 않음
public class PaymentService {

    @Transactional(readOnly = true)   // 조회 메서드만 override
    public List<PaymentHistoryDto> getPaymentHistoriesByMemberId() { ... }
}
```

---

## 4. Redis 역할 정리

| Key 패턴 | 자료구조 | 역할 | TTL |
|---|---|---|---|
| `product_views` | ZSet | 전체 상품 조회수 (인기 랭킹) | 없음 |
| `recentView:{memberId}` | ZSet | 개인별 최근 본 상품 (최대 10개, 최신순) | 7일 |
| `blacklist:{accessToken}` | String | 로그아웃 처리된 JWT | 토큰 만료까지 |
| `refresh:{username}` | String | RefreshToken 저장 | 14일 |

### ZSet (Sorted Set) 동작 원리

```
ProductRankingService — product_views
  ZINCRBY product_views 1 "7"       ← 상품7 조회 시 score +1
  ZREVRANGE product_views 0 9       ← score 내림차순 상위 10개 = 인기 상품

RecentlyViewedService — recentView:42
  ZADD recentView:42 1719407200000 "7"   ← score = 현재시간(ms)
  ZREVRANGE recentView:42 0 9            ← score 내림차순 = 최신순
  ZCARD recentView:42 → 11이면 ZREMRANGE로 가장 오래된 1개 제거
```

---

## 5. N+1 / Index — 내 프로젝트 발생 지점

### N+1 발생 가능 위치

```java
// PaymentService.processPaymentDone()
List<OrderProduct> orderProducts = order.getOrderProducts();  // Lazy 접근
for (OrderProduct op : orderProducts) {
    paymentRepository.save(PaymentHistory.of(op, ...));
}
// → 트랜잭션 안이라 동작은 하지만 OrderProduct 수만큼 추가 쿼리 발생 가능
// → Fetch Join 또는 @EntityGraph로 개선 가능
```

### 내 프로젝트 Index 적용 기준

```
Member.email          → 로그인 조회 (findByEmail) → WHERE 조건 빈번
Cart.member_id        → 장바구니 조회 (findByMemberId) → FK + 빈번한 조회
PaymentHistory.member_id → 결제 내역 조회 (findAllByMember_Id) → 빈번한 조회

index가 없으면 전체 Full Scan → 회원 수 많아질수록 성능 급격히 저하
```

---

## 6. @Transactional 패턴 세트 정리

```
[케이스별 선택 기준]

DB 조회만         → @Transactional(readOnly = true)
DB 쓰기 포함      → @Transactional
외부 API + Checked Exception이 밖으로 나올 때 → @Transactional(rollbackFor = Exception.class)
try-catch로 이미 Runtime 변환 → @Transactional 기본으로 충분

[클래스 vs 메서드 레벨]

클래스 @Transactional      → 모든 메서드의 기본값
메서드 @Transactional(...) → 그 메서드만 통째로 교체 (합산이 아님)

예시:
@Transactional               // 클래스: 기본값
class PaymentService {

    void processPayment() {} // → @Transactional 상속

    @Transactional(readOnly=true)  // → 클래스 설정 완전 교체
    List<Dto> getHistories() {}    //   rollbackFor도 기본값(Runtime)으로 돌아감
}

[Checked Exception 정리]

Unchecked (RuntimeException 하위): NPE, IllegalArgumentException 등
  → @Transactional 기본값으로 롤백됨

Checked (Exception 하위, RuntimeException 아님): IOException, IamportResponseException 등
  → @Transactional 기본값으로 롤백 안 됨
  → 해결 방법 두 가지:
     1. rollbackFor = Exception.class 추가
     2. try-catch로 RuntimeException으로 포장 (추천 — 계층 오염 방지)
```

---

## 6. 보안 처리 흐름

```
모든 /api/v1/cart/**, /api/v1/order/**, /api/v1/payment/** 요청
  ↓
JWTFilterV3 (Filter — Spring 밖)
  - Cookie에서 accessToken 추출
  - Redis blacklist 확인
  - JWTUtil.validateToken() → memberId, role 추출
  - SecurityContextHolder에 Authentication 저장
  ↓
SecurityConfig 인가 검증
  - USER_OR_SELLER_API: ROLE_USER 또는 ROLE_SELLER 필요
  ↓
Controller @AuthenticationPrincipal CustomUserDetails
  - userDetails.getMemberId() → 서비스로 전달
  ↓
Service verifyUserIdMatch(memberId)
  - JWT memberId와 요청 memberId 일치 확인
  - 불일치 시 AccessDeniedException
```
