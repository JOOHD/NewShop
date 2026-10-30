
# NewShop 프로젝트 — 트러블슈팅 & 러닝포인트

> 개발 과정에서 실제로 막혔던 문제 중, 원인을 깊이 파고들었거나 배운 것이 큰 13건만 골라 정리한 기록입니다. (결제 전체 설계는 `PAYMENT_DESIGN.md`에 따로 정리했습니다.)
> 단순한 해결책이 아닌 **"왜 이런 문제가 발생했는가"**를 중심으로 작성했습니다.

---

## 목차

1. [JPA flush 시점 — NOT NULL 제약 위반 (INSERT 이슈)](#1-jpa-flush-시점--not-null-제약-위반-insert-이슈)
2. [JPA Dirty Checking — UNIQUE 제약 충돌 (UPDATE 이슈)](#2-jpa-dirty-checking--unique-제약-충돌-update-이슈)
3. [연관관계 — DB에는 있는데 객체에는 없는 문제](#3-연관관계--db에는-있는데-객체에는-없는-문제)
4. [순환 참조 — SecurityConfig와 Service 의존성](#4-순환-참조--securityconfig와-service-의존성)
5. [상품 상세 페이지 500 에러 — EntityGraph에 List 컬렉션 2개 이상을 동시에 fetch join](#5-상품-상세-페이지-500-에러--entitygraph에-list-컬렉션-2개-이상을-동시에-fetch-join)
6. [상품 목록 N+1 — 상품 수만큼 썸네일 쿼리가 따로 나가는 구조](#6-상품-목록-n1--상품-수만큼-썸네일-쿼리가-따로-나가는-구조)
7. [ddl-auto: update의 함정 — 리네임된 테이블/컬럼이 조용히 갈라지는 문제](#7-ddl-auto-update의-함정--리네임된-테이블컬럼이-조용히-갈라지는-문제)
8. [EC2 디스크 100% — "배포 성공"인데 실제로는 반영 안 되는 문제](#8-ec2-디스크-100--배포-성공인데-실제로는-반영-안-되는-문제)
9. [주문해도 재고가 줄지 않던 문제 + 동시 주문 시 초과 판매](#9-주문해도-재고가-줄지-않던-문제--동시-주문-시-초과-판매)
10. [로그아웃 500 — RefreshToken 삭제가 Member 삭제까지 전파된 cascade](#10-로그아웃-500--refreshtoken-삭제가-member-삭제까지-전파된-cascade)
11. [화면은 할인가, 서버는 정가 — 같은 금액을 두 곳에서 따로 계산한 문제](#11-화면은-할인가-서버는-정가--같은-금액을-두-곳에서-따로-계산한-문제)
12. [중복 결제 방어 — 조회로는 부족했다 (조회 + 행 잠금 + DB UNIQUE)](#12-중복-결제-방어--조회로는-부족했다-조회--행-잠금--db-unique)
13. [서버 재시작 후 주문서 페이지가 로그인 화면으로 튕김 — 페이지 요청이 JWT를 읽지 않던 문제](#13-서버-재시작-후-주문서-페이지가-로그인-화면으로-튕김--페이지-요청이-jwt를-읽지-않던-문제)

---

## 1. JPA flush 시점 — NOT NULL 제약 위반 (INSERT 이슈)

### 문제

DummyProductInitializer로 상품 초기 데이터를 생성했을 때, 로그에는 저장 성공처럼 보였지만 상품 목록 페이지에 데이터가 노출되지 않았다.
DB를 직접 확인하니 `Product` / `ProductManagement` 모두 저장되지 않은 상태였다.

### 원인

`ProductManagement` 생성 시 `nullable = false` 제약이 걸린 `category_id` / `color_id`에 null을 전달했다.
문제는 `save()` 자체가 아니라 **flush 시점**에 있었다.

```
save(product)
→ 영속성 컨텍스트에 등록 (아직 DB INSERT 미수행)

save(productManagement)
→ flush 시점에 INSERT 실행
→ NOT NULL 제약 위반 (category_id / color_id = null)
→ 예외 발생 → 트랜잭션 전체 롤백
```

`save()`는 영속성 컨텍스트 등록이지 DB INSERT가 아니기 때문에,
실제 제약 검증은 flush/commit 시점까지 미뤄지고 — 그 시점에 터졌다.

### 해결

- 기본 `Category` / `Color` 데이터를 사전에 생성한 뒤 참조하도록 구조 개선
- DummyProductInitializer 내에서 필수 연관관계가 null이면 코드 레벨에서 차단

### 러닝포인트

> `save()`는 DB에 즉시 쓰는 게 아니다. "저장 로그는 있는데 DB엔 없음" 현상을 마주치면 **트랜잭션 롤백**부터 의심해야 한다.

---

---

## 2. JPA Dirty Checking — UNIQUE 제약 충돌 (UPDATE 이슈)

### 문제

소셜 로그인 / 계정 활성화 흐름에서 `save()`를 명시적으로 호출하지 않았는데도
트랜잭션 종료 시점에 UPDATE 쿼리가 실행되며 UNIQUE 제약 충돌이 발생했다.

### 원인

**두 가지가 겹쳤다:**

**① Dirty Checking**

```java
Member existedMember = memberRepository.findBySocialId(socialId).get(); // 영속 상태
existedMember.activate(); // 상태 변경만 해도 commit 시점에 UPDATE 발생
// memberRepository.save(existedMember); ← 호출하지 않았음
```

영속 상태 엔티티의 필드를 변경하면, `save()` 없이도 commit 시점에 JPA가 자동으로 UPDATE를 만들어 낸다.

**② 중복 UNIQUE 인덱스**

개발 환경에서 `ddl-auto`로 스키마를 자동 생성하던 중,
`social_id` 컬럼에 동일 기준 UNIQUE 인덱스가 중복 생성되어 있었다.
`activate()` 호출로 발생한 UPDATE가 중복 인덱스와 충돌하며 예외로 이어졌다.

### 해결

1. 중복 UNIQUE 인덱스 제거
2. `social_id`는 Optional 필드이므로 빈 문자열 대신 null로 정규화

```java
String normalized = (socialId == null || socialId.trim().isEmpty()) ? null : socialId;
member.setSocialId(normalized);
```

### 러닝포인트

> JPA에서 영속 엔티티는 `save()` 없이도 commit 시 UPDATE된다.
> UNIQUE 충돌을 분석할 때는 "충돌 원인(스키마)"과 "충돌 발생 시점(Dirty Checking)"을 분리해서 봐야 한다.
> 개발 환경의 `ddl-auto` 자동 생성은 제약 조건 중복을 유발할 수 있어, 운영 환경에서는 지양해야 한다.

---

---

## 3. 연관관계 — DB에는 있는데 객체에는 없는 문제

### 문제

상품-썸네일 이미지는 1:N 관계로 설계했고, 썸네일 데이터는 DB에 정상 저장됐다.
그런데 상품 조회 시 썸네일이 노출되지 않았다.

### 원인

연관관계의 주인(FK) 설정만 하고, **부모 엔티티의 컬렉션에 자식 엔티티를 추가하지 않았다.**

```java
// ❌ FK는 설정되지만 Product 객체의 productThumbnails 리스트엔 추가되지 않음
new ProductThumbnail(product, imageUrl);
```

DB 기준으로는 FK가 연결되어 관계가 존재하지만,
JPA는 **영속성 컨텍스트(객체 그래프)** 기준으로 동작하기 때문에
`product.getProductThumbnails()`는 빈 리스트를 반환했다.

### 해결

연관관계 편의 메서드로 객체와 DB 상태를 동시에 관리:

```java
// Product 엔티티 내부
public void addThumbnail(ProductThumbnail thumbnail) {
    this.productThumbnails.add(thumbnail); // 객체 그래프 연결
    thumbnail.setProduct(this);            // FK 설정
}
```

### 러닝포인트

> JPA는 DB가 아닌 객체 상태를 기준으로 동작한다.
> FK 설정만으로는 객체 그래프가 연결되지 않는다.
> 연관관계 설계는 "어떻게 저장하느냐"가 아닌 **"어떻게 조회하느냐"** 기준으로 결정해야 한다.

---

---

## 4. 순환 참조 — SecurityConfig와 Service 의존성

### 문제

`SecurityConfig`에서 `JWTFilterV3`와 `LoginFilter`를 직접 생성하면서 `MemberService`를 필드 주입으로 받으려 했다.
Spring 초기화 과정에서 SecurityConfig → Filter → MemberService → SecurityConfig로 이어지는 순환 의존성이 발생해 기동에 실패했다.

### 시도 과정

**① `@Lazy` 시도 (실패)**

순환 참조를 지연 초기화로 우회하려 했지만,
Filter 초기화 시점 문제와 디버깅 복잡도 증가로 실질적인 해결이 어려웠다.

**② 메서드 파라미터 주입으로 해결**

```java
// SecurityConfig 내부 — 파라미터로 받아서 직접 전달
JWTFilterV3 jwtFilter = new JWTFilterV3(jwtUtil, redisTemplate, memberService);
var loginFilter = filterFactory.createLoginFilter(authenticationManager, memberService);
```

`MemberService`를 필드가 아닌 메서드 파라미터로 전달함으로써,
Spring Bean 초기화 시점의 순환 참조 자체를 회피했다.

### 러닝포인트

> `@Lazy`는 순환 참조를 "지연"시킬 뿐이고, 실제로 해결하지는 않는다. 디버깅이 더 어려워지는 경우도 있다.
>
> 이 과정에서 더 중요한 것을 발견했다: `SecurityConfig`에서 `MemberService`를 직접 참조하는 것 자체가 **계층 침범**이었다.
> Filter와 Service의 책임을 분리하고, 계층 간 의존 방향을 다시 점검하는 계기가 됐다.

---

---

## 5. 상품 상세 페이지 500 에러 — EntityGraph에 List 컬렉션 2개 이상을 동시에 fetch join

### 문제

라이브 데모에서 아무 상품이나 클릭하면 상세 페이지 대신 아래 에러가 그대로 노출됐다.

```json
{"status":400,"error":"Bad Request","message":"org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags: [Product.productThumbnails, Product.productVariants]"}
```

상품 하나가 아니라 **전체 상품 상세 페이지가 전부** 이 에러로 막혀있었다.

### 원인

`ProductRepository.findProductWithDetailsByProductId()`가 아래처럼 `@EntityGraph`로 세 개의 컬렉션을 한 번에 fetch join 하고 있었다.

```java
@EntityGraph(attributePaths = {"productThumbnails", "productVariants", "wishLists"})
Optional<Product> findProductWithDetailsByProductId(Long productId);
```

`Product`의 `productThumbnails`, `productVariants`, `wishLists`는 전부 `@OneToMany List<...>` — Hibernate 용어로 순서가 없는 "bag" 컬렉션이다. SQL 레벨에서 한 엔티티에 컬렉션 2개를 동시에 JOIN하면 두 컬렉션 크기만큼 행이 카테시안 곱으로 뻥튀기되는데, `List`는 이 중복 행을 걸러낼 기준(정렬 키 등)이 없어서 Hibernate가 이런 조합을 아예 쿼리 생성 단계에서 예외로 막아버린다. `Set`이었다면 허용됐겠지만, 이 프로젝트는 순서가 의미 있는 썸네일/옵션 목록이라 애초에 `List`를 쓴 것이었다.

### 해결

상세 조회는 상품 1건을 대상으로 하는 쿼리라, fetch join 없이 지연 로딩에 맡겨도 실제로는 "본문 쿼리 1개 + 지연로딩 쿼리 2~3개" 수준이라 성능에 영향이 없다. N+1이 문제가 되는 건 "목록 N건을 조회하면서 각 건마다 추가 쿼리가 나가는" 경우이지, 단건 상세 조회에는 해당하지 않는다.

그래서 fetch join은 화면 렌더링에 가장 먼저 필요한 `productThumbnails` 하나만 남기고, 나머지 두 컬렉션은 지연 로딩으로 전환했다.

```java
@EntityGraph(attributePaths = {"productThumbnails"})
Optional<Product> findProductWithDetailsByProductId(Long productId);
```

호출부(`ProductServiceV1`)가 클래스 레벨 `@Transactional` 안에서 실행되기 때문에, `ProductDetailResponseDto` 생성 시점에 `product.getProductVariants()` / `product.getWishLists()`를 호출해도 트랜잭션이 열려있어 지연 로딩이 정상 동작한다.

### 러닝포인트

> `@EntityGraph`/fetch join에 컬렉션을 넣을 때는 "필요하니까 다 넣는다"가 아니라 "몇 건을 조회하는 쿼리인가"부터 따져야 한다. 목록 조회처럼 N건을 반복 조회하는 경우엔 fetch join으로 N+1을 막는 게 맞지만, 단건 상세 조회에서는 지연 로딩 몇 번이 성능에 미치는 영향이 미미하므로 오히려 fetch join을 줄이는 게 안전하다.
>
> 또한 `List` 타입 연관관계 2개 이상을 동시에 fetch join 하려는 시도는 컴파일 타임에는 안 걸리고 **런타임(첫 호출 시점)에만** 터지는 에러라, 로컬 테스트 데이터가 부실하면 미리 못 잡고 배포 후에 발견되기 쉽다 — 실제로 이번에도 로컬에서는 발견하지 못했고, 라이브 데모를 스크린샷 찍으려고 상품을 클릭해보다가 발견했다.

---

---

## 6. 상품 목록 N+1 — 상품 수만큼 썸네일 쿼리가 따로 나가는 구조

### 증상 / 위험

상품 목록은 상품마다 썸네일을 함께 보여 준다. 썸네일이 지연 로딩(LAZY)이라 목록을 가져온 뒤 각 상품의 썸네일에 접근하면, 상품 수만큼 썸네일 조회 쿼리가 따로 나가는 N+1이 된다. 화면에서는 티가 안 나지만 상품이 늘수록 쿼리 수가 같이 늘어난다.

### 원인

`findAll()`은 상품만 조회하고, 연관된 썸네일은 처음 접근하는 순간 상품마다 개별 쿼리로 가져온다.

### 해결

- 목록 조회는 `@EntityGraph(attributePaths = {"productThumbnails"})`를 붙인 메서드(`findAllWithThumbnails`, `findAllForList` 등)로 썸네일을 함께 가져오도록 분리
- `findAll()`을 오버라이드해 전역으로 fetch를 거는 대신, 목록 전용 메서드로 의도를 드러냄(관리자·배치 등 다른 기능까지 썸네일을 끌고 오지 않도록)
- 효과를 `ProductQueryCountTest`로 측정 (상품 10개, Hibernate 통계로 SQL 개수 집계)

| 방식 | 쿼리 수 (상품 10개) |
|---|---|
| `findAll()` 후 썸네일 접근 | 11번 (1 + N) |
| `@EntityGraph` 조회 | 1번 |

### 러닝포인트

> N+1은 상품 하나만 볼 때는 안 보이고 목록에서 터진다. "빨라졌다"가 아니라 쿼리 수를 세는 테스트로 전후를 숫자로 남겨야 근거가 된다. 컬렉션이 둘 이상이라 fetch join을 못 쓰는 경우(`MultipleBagFetchException`, #5)에는 `@BatchSize`가 대안이다.

---

---

## 7. ddl-auto: update의 함정 — 리네임된 테이블/컬럼이 조용히 갈라지는 문제

### 문제

`ProductVariant`의 PK 이름이 `inventoryId`인데, 이 엔티티는 "재고"가 아니라 "상품 옵션(사이즈/성별/색상 조합)"을 나타내서 의미가 안 맞았다. 정리하려고 로컬 DB를 열어봤더니, 코드와 실제 스키마가 이미 상당히 어긋나 있는 걸 발견했다.

- `OrderProduct` 엔티티는 `@Table(name = "order_product")`인데, 로컬 DB엔 `orderproduct`(언더바 없음)만 있고 `order_product`는 아예 없었음
- 지금 코드 어디서도 참조하지 않는 `orders_product_management`라는 테이블이 남아있었음
- `cart.product_mgt_id`, `order_product.product_management_id`처럼 FK 컬럼명도 지금 구조("상품 옵션/variant")와 안 맞는 옛날 이름 그대로였음

### 원인

`ddl-auto: update`는 스키마를 "지금 엔티티 기준으로 동기화"하는 게 아니라 **"없는 건 새로 만든다"**만 한다 — 있는 테이블/컬럼을 리네임하거나 지우는 기능은 없다.

- `orders_product_management`는 `Orders`↔`ProductVariant`를 `@ManyToMany`로 직접 연결하던 옛날 설계가 자동 생성한 암묵적 조인 테이블이다. 이후 `OrderProduct`라는 명시적 중간 엔티티로 리팩토링됐지만, `ddl-auto`는 안 쓰는 옛날 테이블을 알아서 지워주지 않아 그대로 남았다.
- `OrderProduct`의 `@Table` 이름이 `order_product`로 바뀐 뒤로 로컬에서 앱을 한 번도 재부팅하지 않았다. 그래서 "새 이름 테이블이 없으니 만들어야 한다"는 상황 자체가 아직 발생한 적이 없었던 것 — 실제로는 옛날 `orderproduct` 테이블이 계속 쓰이는 채로, 코드만 먼저 `order_product`를 기대하도록 앞서가 있는 상태였다.

만약 이 상태를 모르고 로컬에서 앱을 부팅했다면: Hibernate가 `order_product`가 없는 걸 보고 **텅 빈 새 테이블**을 만들고, 기존 `orderproduct`에 있던 주문 데이터는 앱이 더 이상 조회하지 않는 고아 테이블로 남았을 것이다. 에러 없이 "주문 내역이 갑자기 사라진 것처럼 보이는" 조용한 사고로 이어질 뻔했다.

### 해결

1. `SHOW TABLES;` / `DESC <table>;`로 실제 스키마를 먼저 확인해서 코드와의 차이를 정확히 파악
2. `ALTER TABLE orderproduct RENAME TO order_product;` — 데이터 보존한 채 물리 테이블명만 코드에 맞춤
3. FK 컬럼명도 통일: `ALTER TABLE cart RENAME COLUMN product_mgt_id TO product_variant_id;`, `ALTER TABLE order_product RENAME COLUMN product_management_id TO product_variant_id;`
4. 안 쓰는 `orders_product_management`는 `DROP TABLE`로 제거
5. `ProductVariant`의 PK는 Java 필드명만 `inventoryId` → `variantId`로 바꾸고, 실제 DB 컬럼(`inventory_id`)은 `@Column(name = "inventory_id")`로 고정해서 **DB 마이그레이션 없이** 이름만 정리 (엔티티 필드명과 물리 컬럼명을 분리하면, 운영에 데이터가 있는 PK는 안 건드리고도 코드 가독성만 개선할 수 있다)
6. 겸사겸사 발견한 진짜 죽은 코드도 정리: 예전 "Redis 2단계 주문 흐름" 설계의 잔재였던 `TemporaryOrderRedis` / `RedisOrderRepository` / `TempOrderResponse`가 아무 곳에서도 참조되지 않는 걸 확인하고 삭제, `PaymentService`가 존재하지도 않는 Redis 키(`cartIds:`, `tempOrder:`)를 매번 지우려던 무의미한 호출도 함께 제거

### 러닝포인트

> `ddl-auto: update`는 "동기화"가 아니라 "부족분만 보충"이다. 엔티티의 `@Table`/`@JoinColumn` 이름을 바꾸는 순간부터 코드와 실제 DB가 조용히 갈라지기 시작하고, 그 상태에서 재부팅하면 옛날 데이터가 연결되지 않는 빈 테이블/빈 컬럼이 새로 생긴다 — 에러가 안 나서 오히려 알아채기 더 어렵다.
>
> 엔티티의 물리 이름(`@Table`, `@JoinColumn`, `@Column`)을 바꿀 땐 "DB의 실제 이름도 수동으로 같이 맞춰야 한다"를 세트로 기억할 것. 특히 운영처럼 이미 데이터가 쌓인 환경에서는 **DB 리네임이 코드 배포보다 먼저** 이뤄져야 한다 — 순서가 바뀌면 기존 데이터가 참조 끊긴 채로 남는다.
>
> 참고로 이번 건 "컬럼 리네임 전 것이 그대로 남아있던" 상황이라 조용히 넘어갔지만, 만약 `NOT NULL` 컬럼을 진짜로 새로 추가해야 하는 상황이었다면 기존 행에 채울 기본값이 없어 `ALTER TABLE` 자체가 실패하며 **부팅이 막혔을 수도** 있다. `ddl-auto: update`가 "안전하게 무시하고 넘어간다"고 오해하면 안 되는 이유.

---

---

## 8. EC2 디스크 100% — "배포 성공"인데 실제로는 반영 안 되는 문제

### 문제

GitHub Actions 배포 로그는 매번 초록불(성공)이었는데, 실제 사이트는 몇 커밋 전 상태를 계속 보여주고 있었다. 로컬에서 CSS를 수정하고 배포했는데도 화면이 전혀 바뀌지 않음.

### 원인 진단 과정

1. 배포된 정적 파일을 직접 fetch해서 git의 최신 커밋 내용과 비교 → 서버가 실제로 옛날 버전을 서빙 중임을 확인
2. `git log`/`git diff`로 로컬-원격 커밋 자체는 완전히 동기화되어 있음을 확인 (ahead/behind 0/0) → 코드 문제는 아님
3. EC2에 SSH 접속해서 `df -h /` → 루트 디스크 100% 가득 참 (`7.6G 7.6G 16M 100% /`)
4. `docker images`로 확인해보니 지금까지의 모든 배포 이미지(커밋 SHA 태그)가 하나도 안 지워지고 계속 쌓여있었음 (배포 1회당 약 617MB × 누적 9회)

**진짜 원인**: `deploy.yml`의 정리 명령이 `docker image prune -f`(옵션 없음)였는데, 이 명령은 **태그가 안 붙은(dangling) 이미지만** 지운다. 매 배포마다 이미지에 커밋 SHA + `latest` 두 태그를 붙이는 구조라, 옛날 이미지들은 전부 "태그가 붙어있는" 상태로 분류되어 절대 안 지워지고 계속 쌓였다.

디스크가 가득 차면 `docker compose pull`이 새 이미지를 받다가 조용히 실패하는데, 배포 스크립트에 `set -e`가 없어서 이 실패가 GitHub Actions에 전파되지 않고 파이프라인 전체는 "성공"으로 보고된 것.

### 해결

1. EC2에서 수동으로 `docker image prune -af` 실행 → 약 640MB 확보, 디스크 사용률 100% → 84%로 회복
2. `docker compose pull` + `up -d`로 정상 재배포, `docker inspect`의 `Created` 타임스탬프로 진짜 최신 이미지가 떴는지 검증
3. `deploy.yml`의 정리 명령을 영구적으로 수정해 재발 방지

```yaml
# Before
docker image prune -f

# After
docker image prune -af   # 태그 붙은 옛날 이미지까지 정리
```

### 러닝포인트

> "배포 성공" 로그를 곧이곧대로 믿으면 안 된다 — 스크립트에 `set -e`가 없으면 중간 명령이 실패해도 파이프라인 전체는 성공으로 보고될 수 있다. 배포 결과가 의심스러울 땐 CI 로그가 아니라 **실제로 서버에 떠 있는 것**(정적 파일 내용, 이미지 `Created` 시각)을 직접 확인하는 게 가장 확실하다.
>
> `docker image prune -f`는 "안 쓰는 이미지 정리"가 아니라 "태그 없는 이미지만 정리"다. 커밋 SHA 태그로 매 배포마다 새 이미지를 만드는 구조에서는 `-a` 옵션 없이는 디스크가 무한히 쌓인다 — t2.micro의 작은 루트 디스크(7.57GB)가 실제로 꽉 차고 나서야 체감했다.

---

---

## 9. 주문해도 재고가 줄지 않던 문제 + 동시 주문 시 초과 판매

### 증상

코드를 점검하다 `ProductVariant.decreaseStock()`이 만들어져 있기만 하고 어디서도 호출되지 않는 것을 발견했다. 주문이 들어와도 재고가 줄지 않았다.

### 원인

주문 확정(`confirmOrder`)이 장바구니를 주문으로 옮기는 데만 신경 쓰고 재고 차감 단계가 빠져 있었다. 단순히 차감을 추가하는 것만으로는 부족했다. "조회 → 차감 → 저장"으로 구현하면 동시에 들어온 요청들이 같은 재고 값을 읽고 서로 덮어써 초과 판매가 생긴다.

### 해결

- 주문 확정 시 옵션별 수량을 합산해 재고를 차감하고, 모자라면 예외로 주문 전체를 롤백
- 차감은 DB 조건부 UPDATE 한 문장으로 처리: `재고 >= 수량`일 때만 차감하고, 0건이 갱신되면 재고 부족으로 처리
- 여러 옵션을 한 번에 주문할 때 옵션 ID 오름차순으로 처리해 요청 간 락 순서를 맞춤(데드락 방지)
- 재고 10개에 50개 스레드가 동시에 1개씩 주문하는 테스트(`StockConcurrencyTest`)로 전후 비교

| 방식 | 성공 주문 | 최종 재고 |
|---|---|---|
| 조회 → 차감 → 저장 | 50건 (재고는 10개뿐) | 9 |
| 조건부 UPDATE | 10건 (나머지 40건 재고 부족) | 0 |

### 러닝포인트

> 재고처럼 "읽고 계산해서 다시 쓰는" 값은 애플리케이션에서 계산하지 말고 DB가 검사와 갱신을 한 번에 하게 해야 한다. 그리고 동시성은 말이 아니라 스레드를 여러 개 띄우는 테스트로 재현해 숫자로 확인해야 한다.

(이후 보완: 결제하지 않은 주문은 만료 배치가 30분 뒤 취소하며 재고를 반환하고, 결제 취소 시에도 재고를 복구한다. #11, #12와 `PAYMENT_DESIGN.md` 참고)

---

## 10. 로그아웃 500 — RefreshToken 삭제가 Member 삭제까지 전파된 cascade

### 증상

운영 서버에서 로그아웃을 누르면 500 에러가 났다. 서버 로그의 원인은 `Cannot delete or update a parent row: a foreign key constraint fails (wish_list ... REFERENCES member)`였다. 로그아웃 코드에는 회원을 지우는 부분이 없는데 `member`를 지우려는 쿼리가 나간 것이다.

### 원인

`RefreshToken` 엔티티의 연관관계에 `cascade = CascadeType.REMOVE`가 걸려 있었다.

```java
@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
@JoinColumn(name = "member_id")
private Member member;
```

로그아웃은 `refreshTokenRepository.deleteByRefreshToken()`으로 토큰을 지운다. 토큰(자식)이 삭제되면서 REMOVE가 연결된 Member(부모)까지 전파된 것이다. `cascade`는 **부모에서 자식 방향**으로 걸어야 하는데 자식에서 부모 방향으로 걸려 있었다.

평소에 드러나지 않은 이유는 위시리스트(`wish_list`)가 추가되기 전에는 회원 삭제를 막는 FK가 없었기 때문이다. 위시리스트가 있는 회원만 FK에 막혀 500이 났고, 그렇지 않은 회원은 에러 없이 삭제가 시도됐을 가능성이 있다. 토큰 재발급(`reissue`)도 같은 삭제 메서드를 호출하고 있어 같은 문제를 가지고 있었다.

### 해결

- `RefreshToken.member`에서 `cascade = REMOVE` 제거 (토큰은 회원에 종속되지만 회원은 토큰에 종속되지 않는다)
- `reissue`에서 "기존 토큰 삭제 후 새로 저장" 대신 기존 행을 새 값으로 갱신(Dirty Checking)하도록 변경. 삭제 호출 자체가 없어져 같은 문제가 구조적으로 사라짐

### 러닝포인트

> `cascade`는 "A가 삭제되면 B도 삭제"라는 **생명주기의 소유 관계**다. 소유하는 쪽(부모)에서 소유당하는 쪽(자식)으로만 걸어야 한다. 반대로 걸면 자식(토큰, 임시 데이터)을 지우는 평범한 동작이 부모(회원)의 삭제로 번진다. 이런 설정은 평소에는 조용하다가 FK가 하나 추가되는 순간 드러나므로, 연관관계를 만들 때 "이 엔티티가 삭제되면 무엇이 같이 지워지는가"를 먼저 확인해야 한다.

---

## 11. 화면은 할인가, 서버는 정가 — 같은 금액을 두 곳에서 따로 계산한 문제

### 증상

결제 화면 테스트 중 DB의 주문 합계가 장바구니 화면과 달랐다. 장바구니에는 25% 할인이 적용된 119,625원이 표시됐는데, 주문 테이블에는 정가 159,500원이 저장돼 있었다. 결제 금액 검증이 "서버(DB) 금액 = PG 금액"이라 검증은 통과하므로, 고객에게는 화면보다 39,875원 더 청구될 구조였다. 배송비도 정가 기준으로 계산돼, 할인가가 5만 원 미만이어도 배송비가 붙지 않았다.

### 원인

단가 계산이 두 곳에 있었다.

| 위치 | 단가 |
|---|---|
| 장바구니 화면용(`CartDto`) | 할인율을 적용한 금액 |
| 주문 저장용(`OrderService`) | `product.getPrice()` 정가 |

서버 테스트는 모두 통과했다. 주문 저장 테스트는 "저장된 금액이 맞는지"를 같은 계산식으로 검증했기 때문에 두 계산이 어긋난 것을 알 수 없었다. 실제 화면에서 금액을 눈으로 비교해서야 드러났다.

### 해결

- 단가 계산을 `Product.getDiscountedPrice()`(원 단위 절사) 한 곳으로 모으고 화면용과 주문용이 모두 이 메서드를 쓰게 했다
- 배송비 규칙도 `Orders.calculateDeliveryFee()` 한 곳으로 모았다
- 화면이 금액·배송비를 직접 계산하던 코드를 지우고, 서버의 금액 미리보기 API(`POST /api/v1/order/preview`) 응답만 표시하게 했다
- "화면에 `50000` 같은 금액 규칙이 남아 있지 않다", "미리보기 금액 = 실제 주문 결제 금액"을 테스트로 고정했다
- 실제 환경에서 정가 19,000원 × 17% 할인 = 15,770원이 장바구니, 결제창, DB에 모두 같게 나오는 것을 확인했다

### 러닝포인트

> 돈 계산은 한 곳에서만 한다. 같은 규칙을 화면용과 저장용으로 따로 구현하면 언젠가 어긋나고, 서버 검증이 서버 값을 기준으로 하면 어긋난 값이 그대로 통과한다. 그리고 단위 테스트가 모두 통과해도 화면과 서버가 맞물리는 부분은 실제로 눌러서 비교해야 드러난다.

---

## 12. 중복 결제 방어 — 조회로는 부족했다 (조회 + 행 잠금 + DB UNIQUE)

### 증상

같은 결제번호(`imp_uid`)로 검증 요청이 두 번 오면 결제 이력이 중복 저장될 수 있었다. 처음에는 저장 전에 `existsByImpUid`로 이미 처리된 결제인지 조회해 막았다.

### 원인

"조회 → 저장"은 두 단계라서, 두 요청이 거의 동시에 오면 둘 다 "없음"을 읽고 둘 다 저장한다. 동시 요청 5개를 보내는 테스트(`PaymentDuplicateConcurrencyTest`)로 재현하니 같은 `imp_uid`가 5건 저장됐다. 또 `PaymentHistory`는 주문 상품마다 한 행이라 같은 `imp_uid`가 여러 행에 들어가므로 UNIQUE 제약을 걸 수도 없었다.

### 해결

방어선을 세 겹으로 만들었다.

1. **조회**: 이미 처리된 결제면 조용히 성공으로 돌려보낸다(멱등성, 빠른 경로)
2. **주문 행 잠금**: `SELECT ... FOR UPDATE`로 같은 주문의 결제 검증을 한 번에 하나씩 처리한다
3. **DB UNIQUE**: 결제 1건 = 1행인 `PaymentRecord`를 추가하고 `imp_uid`에 UNIQUE 제약을 걸었다. 코드의 확인이 뚫려도 DB가 마지막으로 막는다

| 방식 | 동시 요청 5개 후 저장된 결제 이력 |
|---|---|
| 조회 후 저장만 | 5건 |
| 주문 행 잠금 | 1건 |

### 러닝포인트

> 중복 방어를 "조회"로만 하면 동시 요청에 뚫린다. 최종 방어선은 코드가 아니라 DB 제약(UNIQUE)이어야 하고, 그 제약을 걸 수 있는 구조(결제 1건 = 1행)로 테이블을 설계해야 한다. 동시성 문제는 스레드를 여러 개 띄우는 테스트로 먼저 재현해서 숫자로 확인한다.

---

## 13. 서버 재시작 후 주문서 페이지가 로그인 화면으로 튕김 — 페이지 요청이 JWT를 읽지 않던 문제

### 증상

로그인한 상태에서 주문서(`/tempOrder`)를 보고 있다가 서버를 재시작하면, 새로고침이나 "바로 주문"에서 로그인 화면이 나왔다. 브라우저에는 JWT 쿠키가 그대로 있고 `/api/...` 호출은 정상 인증되는데 페이지 이동만 막혔다.

### 원인

보안 체인이 둘로 나뉘어 있다. `/api/**`는 STATELESS + JWT, 그 외 모든 주소(페이지)는 세션(`IF_REQUIRED`)을 쓴다. 그런데 `JWTFilterV3.shouldNotFilter`에 `!uri.startsWith("/api")`가 있어서 **페이지 요청에서는 JWT 쿠키를 아무도 읽지 않았다.** 페이지 접근은 서버 메모리의 세션(JSESSIONID)에만 의존했고, 서버를 재시작하면 세션이 사라져 `authenticated()` 경로에서 로그인으로 리다이렉트됐다.

| | 위치 | 서버 재시작 시 |
|---|---|---|
| JWT 쿠키 | 브라우저 | 유지 |
| 서버 세션 | 서버 메모리 | 소멸 |
| JSESSIONID | 브라우저 | 남지만 서버에는 없는 번호 |

재현: 로그인 → 주문서 페이지에서 서버 재시작 → 새로고침 → 로그인 화면. (수정 전 직접 재현 확인)

### 해결

- `!uri.startsWith("/api")` 조건을 제거해 페이지 요청도 JWT 쿠키를 읽게 했다. 로그인/로그아웃/OAuth2 진입점(`/login`, `/formLogin`, `/logout`, `/oauth2/**`, `/login/oauth2/**`)과 정적 리소스만 제외한다.
- 실패 처리를 요청 종류별로 분리했다. API는 기존처럼 401/403 JSON, **페이지는 JSON을 쓰지 않고 인증 없이 통과**시켜 Security가 `/login`으로 리다이렉트하게 했다. (페이지에 JSON 에러가 그대로 보이는 것을 막기 위해)
- `filterChain.doFilter`를 try 밖으로 옮겼다. 하위 로직(컨트롤러)의 예외가 "JWT 인증 실패 401"로 잘못 포장되던 것도 함께 정리됐다.
- 세션은 그대로 둔다. JWT가 만료/무효여도 세션에 인증이 남아 있으면 기존처럼 접근되므로 퇴보가 없다.

### 검증

`JWTFilterV3PageRequestTest`: 페이지+유효 토큰은 세션 없이 인증, 페이지+만료/블랙리스트는 JSON 없이 비인증 통과, API+만료는 401, API+블랙리스트는 403, 토큰 없음은 통과, 제외 경로/대상 경로 판정. 수동 확인(수정 후): 로그인 → 주문서에서 서버 재시작 → 새로고침해도 로그인이 유지되는 것을 직접 확인했다.

### 러닝포인트

> 인증 수단이 둘(JWT, 세션)이면 "어느 요청이 어느 쪽을 보는지"를 명시해야 한다. 한쪽 경로에서 필터를 통째로 건너뛰면 평소엔 세션 덕에 가려지다가, 서버 재시작처럼 세션이 사라지는 순간에만 드러난다. 그리고 같은 필터라도 API와 페이지는 실패 응답 형식(JSON vs 리다이렉트)이 달라야 한다.

---

## 부록 — 이 프로젝트에서 사용한 핵심 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot, Spring Security (Dual Filter Chain), Spring Data JPA |
| 인증 | JWT (HttpOnly Cookie), OAuth2 (Kakao / Naver), Redis Blacklist |
| 주문/결제 | Cart→DB 직접 저장, Iamport 서버 검증 |
| 성능 | Redis ZSet (조회수 랭킹), Fetch Join / EntityGraph (N+1 대응) |
| Frontend | Thymeleaf SSR, Fetch API, jQuery |
| Infra | MySQL, Docker |

📄 [API 문서 (Postman)](https://documenter.getpostman.com/view/16649127/2sB2cUC3Qn)
