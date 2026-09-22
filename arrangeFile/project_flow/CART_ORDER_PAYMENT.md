# JooShop 장바구니 → 주문 → 결제 흐름 정리

> 최종 리팩토링 기준 (2026-06).
> Redis 임시주문 제거, confirmOrder 단순화, 최근 본 상품 Redis ZSET 추가.

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
 ├─ 주문 확정 (POST /api/v1/order/confirm)
 │       └─→ OrderService.confirmOrder()
 │               ├─ Cart 조회 → 소유자 검증
 │               ├─ Orders.createOrder() + OrderProduct 추가
 │               └─ DB 저장 (@Transactional 보호)
 │
 ├─ 결제 완료 알림 (POST /api/v1/payment/{imp_uid})
 │       └─→ PaymentController
 │               ├─ Iamport 서버 재조회 (금액 위조 검증)
 │               └─→ PaymentService.processPaymentDone()
 │                       ├─ Order 상태 COMPLETE (Dirty Checking)
 │                       ├─ PaymentHistory INSERT
 │                       └─ Redis 정리
 │
 └─ 결제 취소 (POST /api/v1/payment/cancel/{paymentHistoryId})
         └─→ PaymentService.cancelPayment()
                 ├─ Iamport 취소 API 호출 (try-catch)
                 ├─ markCanceled() (API 성공 후에만)
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

  ⑤ Orders.createOrder(member, ordererName, phone, postCode, address, payMethod, merchantUid)
     ← 팩토리 메서드. new Orders() 직접 생성 금지

  ⑥ carts.forEach(cart → order.addOrderProduct(orderProductFromCart(cart)))
     → Cart → OrderProduct 변환 (상품명, 가격, 수량 스냅샷)
     → 주문 당시 정보 고정 (이후 상품 수정/삭제돼도 이력 보존)

  ⑦ orderRepository.save(order)
     → Orders INSERT + OrderProduct INSERT (CASCADE)
     → 여기서 예외 발생 시 전체 롤백

Response: "주문이 완료되었습니다. 주문번호: 15"
```

### 메서드 로드맵

| 메서드 | 트랜잭션 | 설명 |
|---|---|---|
| `confirmOrder(orderDto)` | `@Transactional(rollbackFor=Exception.class)` | 유일한 메서드. Cart → DB 직접 저장 |

### 예외 발생 구간

```
confirmOrder:
  - cartIds 비어있음 → IllegalArgumentException("주문할 장바구니 항목이 없습니다")
  - 타인 Cart 접근 → verifyUserIdMatch 실패
  - orderRepository.save 실패 → 전체 롤백
```

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
  body: { memberId:42, orderId:15, price:189000 }
  ▼
PaymentController.validateIamport(impUid, request)

  ① iamportClient.paymentByImpUid(impUid)
     → Iamport 서버에 직접 재조회 (서버-서버 통신)
     → 클라이언트 데이터 신뢰 안 함 (금액 위조 공격 방어)

  ② 금액 위조 검증
     iamportAmount = 189000  (Iamport 실제 결제금액)
     requestAmount = 189000  (클라이언트 요청금액)
     iamportAmount.compareTo(requestAmount) != 0 → IllegalArgumentException

  ③ paymentService.processPaymentDone(iamportPayment, request)
  ▼
PaymentService.processPaymentDone()
  @Transactional (클래스 기본값)

  ① verifyUserIdMatch(42)
  ② orderRepository.findById(15) → Orders 조회
     없으면 → OrderNotFoundException("주문 정보를 찾을 수 없습니다")
  ③ order.changePaymentStatus(COMPLETE) ← Dirty Checking → 커밋 시 UPDATE 자동 실행
  ④ order.getOrderProducts() 순회 → PaymentHistory 생성 → paymentRepository.save()
  ⑤ Redis 정리: "cartIds:42", "tempOrder:42" 삭제

커밋 → Order status UPDATE + PaymentHistory INSERT
```

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
| `processPaymentDone(response, request)` | 클래스 기본 `@Transactional` | 결제 완료 처리. Order 상태 + PaymentHistory 저장 |
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
| `cartIds:{memberId}` | String | 결제 완료 후 삭제용 임시 키 | — |
| `tempOrder:{memberId}` | String | 결제 완료 후 삭제용 임시 키 | — |
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
