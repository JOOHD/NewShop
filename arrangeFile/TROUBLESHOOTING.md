# NewShop 프로젝트 — 트러블슈팅 & 러닝포인트

> 개발 과정에서 실제로 막혔던 문제들과 그 원인, 해결 과정을 정리한 기록입니다.
> 단순한 해결책이 아닌 **"왜 이런 문제가 발생했는가"**를 중심으로 작성했습니다.

---

## 목차

1. [JPA flush 시점 — NOT NULL 제약 위반](#1-jpa-flush-시점--not-null-제약-위반-insert-이슈)
2. [JPA Dirty Checking — UNIQUE 제약 충돌](#2-jpa-dirty-checking--unique-제약-충돌-update-이슈)
3. [연관관계 — DB에는 있는데 객체에는 없는 문제](#3-연관관계--db에는-있는데-객체에는-없는-문제)
4. [순환 참조 — SecurityConfig와 Service 의존성](#4-순환-참조--securityconfig와-service-의존성)

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

## 부록 — 이 프로젝트에서 사용한 핵심 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot, Spring Security (Dual Filter Chain), Spring Data JPA |
| 인증 | JWT (HttpOnly Cookie), OAuth2 (Kakao / Naver), Redis Blacklist |
| 주문/결제 | Redis 임시 주문, Iamport 서버 검증 |
| 성능 | Redis ZSet (조회수 랭킹), Fetch Join / EntityGraph (N+1 대응) |
| Frontend | Thymeleaf SSR, Fetch API, jQuery |
| Infra | MySQL, Docker |

📄 [API 문서 (Postman)](https://documenter.getpostman.com/view/16649127/2sB2cUC3Qn)
