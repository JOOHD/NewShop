# JooShop — Spring Boot 쇼핑몰 백엔드

> Spring Boot 3 기반 쇼핑몰 백엔드 프로젝트.
> JWT 쿠키 인증 · OAuth2 소셜 로그인 · DDD Aggregate Root · Redis 2단계 주문 흐름 · Iamport 결제 연동을 직접 설계·구현했습니다.

🔗 [API 문서 (Postman)](https://documenter.getpostman.com/view/16649127/2sB2cUC3Qn)

---

## 기술 스택

| 분류 | 기술 | 선택 이유 |
|---|---|---|
| Language | Java 17 | Record, sealed class 등 최신 문법 활용 |
| Framework | Spring Boot 3 / Spring Security 6 | 최신 Security 아키텍처 (SecurityFilterChain 분리) |
| ORM | Spring Data JPA + QueryDSL | 정적 쿼리는 파생쿼리, 동적 필터링은 QueryDSL로 분리 |
| Database | MySQL 8 | 트랜잭션 / FK / UNIQUE 제약 기반 무결성 관리 |
| Cache / 상태 | Redis | Refresh Token 블랙리스트, 임시 주문, 조회수 ZSet |
| 인증 | JWT (HttpOnly Cookie) + OAuth2 | Stateless + XSS 방어 동시 달성 |
| 결제 | Iamport (포트원) REST | 클라이언트 위변조 방지 서버 검증 구조 |
| View | Thymeleaf | SSR 기반, JS/jQuery Fetch로 동적 처리 |
| 빌드 | Gradle | |

---

## 아키텍처 & 핵심 설계 결정

### 레이어드 아키텍처 — 단방향 의존

```
Controller  →  Service  →  Repository
                ↑
           도메인 메서드 (Entity)
```

- Controller는 요청/응답 파싱만. 비즈니스 로직 없음.
- Service가 타 도메인 정보를 필요로 할 때 **Repository 직접 참조 금지** — 반드시 해당 도메인 Service를 통해서만 접근.
- 예: `CartService`가 회원 정보 필요 시 `MemberRepository` 직접 참조 → `MemberAccountService.findMemberById()`로 변경.

### DDD Aggregate Root — Setter 없는 엔티티

`Member` / `Orders` / `Product`를 Aggregate Root로 지정.
외부에서 상태를 직접 변경할 수 없고 반드시 도메인 메서드를 통해서만 변경.

```java
// ❌ 안티패턴 — 의도가 없고, 검증도 없고, 어디서든 바꿀 수 있음
member.setStatus("BANNED");

// ✅ 도메인 메서드 — 의도가 드러나고, 내부 검증 포함
member.ban();
member.activate();
order.complete();
```

- `@NoArgsConstructor(access = PROTECTED)` — JPA 복원용 통로만 열고 외부 `new` 차단
- 생성은 정적 팩토리 메서드(`Member.registerGeneral()`, `Orders.createOrder()`)로만 허용
- DTO는 `static from(Entity)` 패턴으로 변환 책임 분리

### SecurityFilterChain 이중 분리

단일 체인으로 REST API와 브라우저 요청을 함께 처리하면 **에러 응답 형식(JSON vs 리다이렉트)**과 **세션 전략(Stateless vs 세션 유지)**이 충돌한다.

```
Order 1: apiSecurityFilterChain  →  /api/**
  STATELESS, CSRF 비활성, JWT 검증, JSON 401/403 응답

Order 2: webSecurityFilterChain  →  /**
  Form Login, OAuth2, CSRF 활성, 리다이렉트 응답
```

두 체인을 분리함으로써 REST 클라이언트와 브라우저 각각의 요구사항을 독립적으로 충족.

---

## 주요 기능 구현

### 1. 인증 — JWT + HttpOnly Cookie + Redis Blacklist

**왜 localStorage 대신 HttpOnly 쿠키인가?**

localStorage에 JWT를 저장하면 XSS 공격으로 토큰 탈취가 가능하다.
HttpOnly 쿠키는 JavaScript에서 접근이 불가하여 XSS로 토큰을 읽어갈 수 없다.

**왜 Access + Refresh 이중 토큰인가?**

Access Token만 쓰면 만료 시 로그인을 다시 해야 한다.
Refresh Token을 DB + Redis에 저장하여 Access 만료 시 재발급, **서버에서 Refresh 자체를 무효화(로그아웃/탈취 시 삭제)**할 수 있다.

**로그아웃 — Redis Blacklist**

JWT는 Stateless라 서버에서 토큰을 강제 무효화할 수 없는 게 약점이다.
로그아웃 시 **Access Token을 Redis에 블랙리스트로 등록 (남은 만료 시간 TTL)** 하여 해당 토큰이 다시 사용되면 403으로 차단.

```
로그아웃 요청 (POST /logout)
  → Access Token Redis 블랙리스트 등록 (TTL = 토큰 남은 시간)
  → Refresh Token DB 삭제
  → HttpOnly 쿠키 만료 처리
```

**TokenCookieWriter — 환경별 쿠키 정책 중앙화**

로그인 성공 핸들러(Form, OAuth2, 이메일 인증)마다 쿠키 코드가 중복되어 있었다.
`TokenCookieWriter` 하나로 집중하고 `app.secure` 값 하나로 로컬↔운영 쿠키 정책을 자동 분기.

```
로컬: SameSite=Lax, Secure=false
운영: SameSite=None, Secure=true (OAuth2 크로스 도메인 대응)
```

**로그인 방식**

```
POST /formLogin        → FormLoginSuccessHandler  → JWT 쿠키 발급
GET  /oauth2/{kakao|naver} → OAuth2LoginSuccessHandler → JWT 쿠키 발급
```

Form Login + OAuth2 통합 흐름 모두 `TokenService.issueLoginTokens()`로 토큰 발급 일원화.

---

### 2. 주문/결제 — Redis 2단계 주문 전략

**문제 의식**: 결제 도중 이탈하거나 결제가 실패했을 때 DB에 미완료 주문이 남으면 데이터 정합성이 깨진다.

**해결 — Redis 임시 저장 → 결제 성공 후 DB 영구 저장**

```
장바구니 선택 → Redis 임시 주문 저장 (order:{memberId})
  → Iamport 결제 요청 (클라이언트)
  → imp_uid 서버 전달
  → 서버: Iamport API 재조회 → 금액 일치 검증
  → 검증 통과: Redis → Orders/OrderProduct DB 변환 저장
  → 실패/이탈: Redis TTL 만료로 자동 소멸 (DB 흔적 없음)
```

**왜 Iamport 서버 재검증인가?**

클라이언트에서 결제 금액을 조작하여 서버에 전달할 수 있다.
서버가 `imp_uid`로 Iamport API를 직접 호출하여 실제 결제 금액을 재조회하고 주문 금액과 비교 — **클라이언트 위변조를 서버에서 차단**.

---

### 3. 상품 — QueryDSL 동적 필터링 + Redis 조회수 랭킹

**QueryDSL 동적 필터링**

조건(할인/추천/카테고리)과 정렬(최신/가격 오름차순·내림차순)을 조합한 동적 쿼리를 JPQL 문자열이 아닌 타입 안전한 QueryDSL `BooleanBuilder`로 조립.
파라미터가 없으면 조건을 추가하지 않는 방식으로 `null` 조건 누락 버그를 원천 차단.

**Redis ZSet 실시간 조회수 랭킹**

```java
// 상품 조회 시 score 누적
redisTemplate.opsForZSet().incrementScore("product_views", productId, 1);

// 상위 N개 ID 조회 후 DB에서 상품 정보 조회
redisTemplate.opsForZSet().reverseRange("product_views", 0, limit - 1);
```

ZSet을 선택한 이유: score(조회수) 기준 정렬이 내장되어 별도 정렬 쿼리 없이 랭킹 조회 가능.

---

### 4. 인가 — @RequiresRole AOP

Spring Security 기본 `@PreAuthorize`는 Controller 레벨에서만 역할 검증이 된다.
**Service 메서드 단위** 역할 검증이 필요해 커스텀 `@RequiresRole` 어노테이션을 AOP로 직접 구현.

```java
@RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
public Long createProduct(ProductRequestDto dto, Long memberId) { ... }
```

Controller가 아닌 Service에서 검증하므로 API 경로가 변경되어도 인가 로직은 Service에 고정.

---

### 5. 예외 처리 — GlobalExceptionHandler 일원화

각 Controller에 흩어져 있던 `try-catch` 블록을 `@RestControllerAdvice`로 일원화.
커스텀 예외 + 표준 응답 DTO(status / message / code)로 에러 응답 형식 통일.

```
인증 예외 → 401
도메인 예외 (회원 없음, 재고 없음) → 404 / 400 + 메시지
검증 예외 (@Valid 실패) → 400 + 필드별 메시지
결제 예외 → 500 + 메시지
```

Controller는 정상 흐름만 작성. 예외 처리는 GlobalExceptionHandler에서만.

---

## 트러블슈팅

### 1. JPA `save()` 했는데 DB에 없다 — flush 시점 오해

**증상**: `save()` 로그는 출력됐는데 DB에 데이터가 없음. 상품 목록 페이지 빈 화면.

**원인 분석**: `save()`가 DB에 즉시 INSERT하는 게 아니라는 점을 놓쳤다.

```
save(product)          → 영속성 컨텍스트 등록 (DB INSERT 아직 아님)
save(productManagement) → flush 시점에 INSERT 실행
                          → category_id = null (NOT NULL 제약 위반)
                          → 예외 발생 → 트랜잭션 전체 롤백
```

"저장 로그는 있는데 DB엔 없다" = **트랜잭션 롤백**이 원인이었다.

**해결**: 필수 연관 엔티티(Category, Color)를 사전에 DB에 확보한 뒤 참조.
코드 레벨에서 null 여부를 미리 체크하여 flush 시점 이전에 차단.

**러닝포인트**: `save()`는 "영속성 컨텍스트 등록"이지 "DB INSERT"가 아니다. 실제 DB 반영은 flush/commit 시점. 제약 위반은 그때 터진다.

---

### 2. `save()` 안 불렀는데 UPDATE 쿼리가 나간다 — Dirty Checking + 스키마 충돌

**증상**: 소셜 로그인 활성화 흐름에서 `save()` 호출 없이 UNIQUE 제약 충돌 발생.

**원인 분석 — 두 가지가 겹쳤다**

① **Dirty Checking**: `findBySocialId()`로 조회한 영속 엔티티에서 `activate()`만 호출했을 뿐인데, 트랜잭션 종료 시 JPA가 변경을 감지하여 자동 UPDATE를 생성.

```java
Member member = memberRepository.findBySocialId(socialId).get(); // 영속 상태
member.activate(); // save() 없음 → commit 시 JPA가 자동 UPDATE 생성
```

② **중복 UNIQUE 인덱스**: 개발 환경 `ddl-auto` 자동 생성 과정에서 `social_id` 컬럼에 동일 기준 인덱스가 중복 생성되어 있었다. 자동 UPDATE가 이 중복 인덱스와 충돌.

**해결**: 중복 인덱스 제거 + `social_id` 빈 문자열을 null로 정규화하여 UNIQUE 기준 일원화.

**러닝포인트**: JPA에서 영속 엔티티는 `save()` 없이도 commit 시 변경이 DB에 반영된다. "UNIQUE 충돌"을 디버깅할 때는 **"왜 UPDATE가 발생했나"** 와 **"왜 충돌하는 데이터가 있나"** 를 분리해서 보아야 한다.

---

### 3. DB에는 있는데 객체에는 없다 — 양방향 연관관계 일관성

**증상**: 썸네일 데이터가 DB에 정상 저장됐는데 상품 조회 시 썸네일 리스트가 비어있음.

**원인 분석**: FK(연관관계 주인)만 설정하고 부모 엔티티의 컬렉션에 추가하지 않았다.

```java
// ❌ DB FK는 연결됐지만 Product 객체의 리스트엔 추가 안 됨
new ProductThumbnail(product, imageUrl);

// product.getProductThumbnails() → 빈 리스트 반환
```

JPA는 DB가 아닌 **영속성 컨텍스트(객체 그래프)** 기준으로 동작하기 때문에, DB에 FK가 연결되어 있어도 객체 그래프에 추가되지 않으면 조회 시 비어있다.

**해결**: 연관관계 편의 메서드로 객체와 DB 상태를 동시에 관리.

```java
public void addThumbnail(ProductThumbnail thumbnail) {
    this.productThumbnails.add(thumbnail); // 객체 그래프 연결
    thumbnail.setProduct(this);            // FK 설정
}
```

**러닝포인트**: 양방향 연관관계에서 한쪽만 설정하면 DB와 객체 상태가 불일치한다. 편의 메서드로 항상 양쪽을 동시에 관리.

---

### 4. 순환 참조 — SecurityConfig Bean 초기화 순서 문제

**증상**: 애플리케이션 기동 시 `BeanCurrentlyInCreationException` 발생.

**원인 분석**: `SecurityConfig`에서 `JWTFilterV3` 생성 시 `MemberService`를 필드 주입으로 받으려 했는데, Spring Bean 초기화 순서상 순환 참조가 발생.

```
SecurityConfig (초기화 중)
  → JWTFilterV3 생성 → MemberService 필요
  → MemberService (초기화 중) → SecurityConfig 참조
  → 순환 참조 → 기동 실패
```

**① `@Lazy` 시도 → 실패**: 지연 초기화로 우회했지만 Filter 초기화 시점 문제가 해결되지 않았고 디버깅이 더 복잡해졌다.

**② 최종 해결 — 메서드 파라미터로 전달**

```java
// SecurityConfig에서 Bean을 필드로 주입받지 않고 메서드 파라미터로 전달
JWTFilterV3 jwtFilter = new JWTFilterV3(jwtUtil, redisTemplate, memberService);
```

Bean 초기화 시점의 순환 참조 자체를 회피.

**러닝포인트**: `@Lazy`는 순환 참조를 "지연"시킬 뿐이지 해결이 아니다. 더 중요한 것은 **SecurityConfig가 MemberService를 직접 참조하는 것 자체가 계층 침범**이었다는 점 — 이 트러블슈팅을 계기로 FilterFactory 패턴으로 분리했다.

---

## 리팩토링 이력 — 설계 개선 과정

이 프로젝트는 초기 구현 후 구조적인 문제를 발견하고 리팩토링한 이력이 있다.

### Form Login 단일화 — JSON API 로그인 제거

**이전**: `/api/login` JSON 엔드포인트(LoginFilter)와 Form Login이 공존.
**문제 의식**: 브라우저 기반 쇼핑몰에서 JSON API 로그인은 불필요한 복잡도. Spring Security 표준 흐름을 우회하는 커스텀 필터를 유지하는 비용이 크다.
**결과**: `LoginFilter`, `CustomJsonEmailPasswordAuthenticationFilter`, `MemberAuthService` 삭제. Form Login 단일화로 Spring Security 표준 흐름 활용.

### 인증 계층 분리 — JWT 계층이 Member 도메인을 직접 건드리지 않도록

**이전**: `JWTFilter`, `LoginFilter`에 토큰 생성·저장 로직이 섞여 있었고, `SecurityContext`에 `Member` 엔티티를 직접 저장.
**문제**: 인증 인프라 코드가 Member 도메인을 오염시킴. 필터가 너무 많은 책임.
**결과**:
- `TokenService`로 JWT 발급/재발급 비즈니스 로직 집중
- `JWTUtil`은 생성/파싱/검증 순수 유틸로만 제한
- `SecurityContext`에는 `Member` 엔티티 대신 `CustomUserDetails`(Snapshot DTO) 저장
- 인증 계층과 도메인 계층 경계 확립

### CustomLogoutFilter — Spring 내장 LogoutFilter 버그 해결

**이전**: Spring Security 내장 LogoutFilter 사용.
**문제**: Spring 기본 `LogoutFilter`(order ~900)가 `CustomLogoutFilter`(order ~1299)보다 먼저 실행되어, Redis 블랙리스트 등록과 쿠키 초기화가 되기 전에 로그아웃 처리가 완료됐다.
**결과**: 내장 LogoutFilter를 비활성화하고 `CustomLogoutFilter`가 `POST /logout`을 단독 처리하도록 변경. 필터 실행 순서 문제 해결.

### RestTemplate → WebClient 전환 (KakaoOAuthClient)

**이전**: `new RestTemplate()`으로 매번 생성 (커넥션 풀 없음) + Spring 6 deprecated.
**결과**: `WebClient` Bean으로 등록하여 재사용, `.onStatus()`로 4xx/5xx 에러 선언적 처리.

```java
return webClient.post()
    .uri(KAKAO_TOKEN_URI)
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError, response ->
        response.bodyToMono(String.class)
            .map(body -> new IllegalStateException("카카오 토큰 요청 실패: " + body))
    )
    .bodyToMono(OAuthTokenResponse.class)
    .block();
```

---

## 개선 방향 (Self-review)

| 항목 | 현재 | 개선 방향 |
|---|---|---|
| 배포 | 로컬 실행 | Docker + EC2 + RDS + S3 구성 |
| 이미지 저장 | 외부 URL 참조 | S3 직접 업로드 + presigned URL |
| 테스트 | 없음 | Service 레이어 단위 테스트 작성 |
| 모니터링 | 없음 | Spring Actuator + 로그 집계 |
| HTTPS | 없음 | Nginx + Let's Encrypt SSL |
| 결제 검증 | Iamport 서버 검증 | 웹훅 기반 이중 검증 추가 |

---

## Redis 활용 전략 요약

| 용도 | 키 패턴 | TTL | 자료구조 |
|---|---|---|---|
| JWT 블랙리스트 | `blacklist:{accessToken}` | 토큰 남은 만료 시간 | String |
| Refresh Token | `refresh:{memberId}` | 7일 | String |
| 임시 주문 | `order:{memberId}` | 결제 완료 전 | Hash |
| 조회수 랭킹 | `product_views` | 영구 | ZSet |
| 프로필 이미지 캐시 | `profileImages::{memberId}` | 60분 | String |

---

## 인증 흐름 요약

```
[Form Login]
POST /formLogin → UsernamePasswordAuthFilter → CustomUserDetailsService
→ BCrypt 검증 → FormLoginSuccessHandler → TokenService → TokenCookieWriter → 쿠키 발급

[OAuth2]
GET /oauth2/{provider} → CustomOAuth2UserService → OAuth2MemberService (findOrCreate)
→ OAuth2LoginSuccessHandler → TokenService → TokenCookieWriter → 쿠키 발급

[매 요청]
JWTFilterV3 → Redis blacklist 확인 → JWTUtil 검증 → SecurityContext 세팅

[재발급]
POST /api/v1/reissue → TokenService.reissue() → 기존 삭제 → 새 토큰 발급

[로그아웃]
POST /logout → CustomLogoutFilter → Redis 블랙리스트 등록 + DB 삭제 + 쿠키 만료
```
