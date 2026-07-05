# JooShop — Spring Boot E-Commerce Platform

> Spring Boot 3 기반 쇼핑몰 백엔드.  
> JWT 쿠키 인증, OAuth2 소셜 로그인, DDD Aggregate Root 설계를 중심으로 구현.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| **Language** | Java 17 |
| **Framework** | Spring Boot 3, Spring Security 6, Spring Data JPA |
| **Database** | MySQL 8, Redis |
| **Auth** | JWT (Access/Refresh Cookie), OAuth2 (Kakao, Naver), Form Login |
| **Payment** | Iamport (포트원) REST Client |
| **View** | Thymeleaf (Server-side rendering) |
| **Query** | QueryDSL (동적 상품 검색/정렬) |
| **Infra** | Redis — 토큰 블랙리스트, 임시 주문, 이미지 캐싱 |

---

## 아키텍처 & 설계 방향

### 레이어드 아키텍처

```
Controller  ← 요청/응답 파싱, 라우팅만 담당. 비즈니스 로직 없음
Service     ← 비즈니스 로직 집중. 도메인 메서드 호출, 트랜잭션 관리
Repository  ← DB 연결. 쿼리 정의
Entity/DTO  ← Entity: 도메인 상태.  DTO: 계층 간 데이터 전달 전용
```

레이어 간 단방향 의존 (`Controller → Service → Repository`).  
Service가 타 도메인에 접근할 때도 Repository 직접 참조 대신 해당 도메인 Service를 통해서만 접근.

### DDD Aggregate Root 패턴

`Member`, `Orders`, `Product`를 Aggregate Root로 설계. 외부에서 도메인 상태를 직접 변경할 수 없고, 반드시 엔티티의 도메인 메서드를 통해서만 변경.

```java
// ❌ 안티패턴 — Setter로 상태 직접 변경
member.setStatus("BANNED");

// ✅ 도메인 메서드 — 의도가 드러나고 내부에서 유효성 검증
member.ban();
```

| 원칙 | 구현 방식 |
|------|-----------|
| 외부 직접 생성 금지 | `@NoArgsConstructor(access = PROTECTED)`, 퍼블릭 생성자 미노출 |
| Setter 노출 금지 | `@Setter` 제거, 상태 변경은 의미 있는 도메인 메서드로 |
| 팩토리 메서드 | `Member.registerGeneral()`, `Orders.createOrder()`, `Product.create()` |
| 상태 변경 캡슐화 | `member.ban()`, `member.activate()`, `order.complete()` 등 |

### DTO 설계 원칙

| 유형 | 어노테이션 | 이유 |
|------|------------|------|
| 요청 DTO (Request) | `@Getter` only | 서버에서 필드를 바꿀 이유 없음 |
| 응답 DTO (Response) | `@Getter` + `private final` | 읽기 전용, 불변 |
| 변환 메서드 | `static from(Entity)` 또는 생성자 | `toEntity()`는 DTO가 Entity를 생성한다는 오해를 유발 |

`@Data` 사용 금지 — 응답 DTO에 불필요한 Setter가 노출됨.

---

## 모듈 구조

```
JOO.jooshop
│
├── global/
│   ├── authentication/         # 인증 핵심 인프라
│   │   ├── jwts/               # JWT 필터, 핸들러, 유틸, 서비스
│   │   └── oauth2/             # OAuth2 소셜 로그인 처리
│   ├── authorization/          # 역할 기반 인가 (@RequiresRole AOP)
│   ├── config/                 # Spring 설정 (Security, Redis, Iamport 등)
│   ├── exception/              # GlobalExceptionHandler
│   ├── mail/                   # 이메일 인증 서비스
│   ├── image/                  # 이미지 처리 유틸
│   └── dummy/                  # 로컬 개발용 더미 데이터 초기화
│
├── members/                    # 회원 도메인 (Aggregate Root: Member)
├── order/                      # 주문 도메인 (Aggregate Root: Orders)
├── product/                    # 상품 도메인 (Aggregate Root: Product)
├── payment/                    # 결제 도메인 (Iamport 연동)
├── cart/                       # 장바구니
├── address/                    # 배송 주소
├── profile/                    # 프로필 (이미지, 나이, 성별)
├── Inquiry/                    # 상품 문의 / 답변
├── wishList/                   # 위시리스트
├── categorys/                  # 상품 카테고리
├── productManagement/          # 상품 옵션/재고 관리
├── thumbnail/                  # 상품 대표 이미지
├── contentImages/              # 상품 상세 이미지
└── admin/                      # 관리자 (회원, 상품, 주문 관리)
```

---

## 도메인별 설계 포인트

### Members — 회원

역할: `USER`, `SELLER`, `ADMIN` 3단계. 상태: `ACTIVE`, `INACTIVE`, `BANNED`.

`MemberAccountService`를 회원 조회/상태 변경의 **단일 진입점**으로 설계. 타 도메인(AddressService, ProfileService, InquiryService 등)이 회원 정보가 필요할 때 `MemberRepository`를 직접 참조하지 않고 `MemberAccountService`를 통해서만 접근 — 도메인 간 레이어 의존 방향 준수.

```java
// ✅ 타 도메인 서비스에서 회원 조회
Member member = memberAccountService.findMemberById(memberId);
```

### Orders — 주문

**임시 주문(Redis) → 결제 완료 후 DB 영구 저장** 2단계 전략.

결제 완료 전까지 주문 데이터를 Redis(`order:{memberId}`)에만 유지. 결제 도중 이탈 시 TTL에 의해 자동 소멸되어 DB에 미완료 주문이 쌓이지 않음. 결제 검증 성공 후에야 `Orders` 엔티티를 DB에 저장.

```
결제 요청 → Redis 임시 저장 → Iamport 결제 → 서버 검증 → DB 저장
```

### Products — 상품

QueryDSL로 조건(할인/추천/카테고리)·정렬(최신/가격)·검색어를 조합한 **동적 필터링** 구현. `ProductQueryHelper`에서 `BooleanBuilder`를 빌드하고 `ProductOrderService`에서 실행.

**Redis 기반 실시간 조회수 랭킹**: 상품 조회 시 Redis ZSet에 조회수를 누적(`ZINCRBY`). `getTopProductIds(limit)`으로 상위 N개 ID를 읽어 DB 조회 후 DTO로 변환.

```java
// 조회수 기록
redisTemplate.opsForZSet().incrementScore("product_views", productId, 1);

// 랭킹 조회
redisTemplate.opsForZSet().reverseRange("product_views", 0, limit - 1);
```

### Payments — 결제

Iamport(포트원) REST API 연동. 클라이언트에서 결제 완료 후 `imp_uid`를 서버에 전달 → 서버가 Iamport API로 **결제 금액을 직접 재조회하여 검증** (클라이언트 위변조 방지). 검증 통과 후 DB에 결제 이력 저장. 취소도 서버에서 Iamport API 호출.

```
클라이언트 결제 완료 → imp_uid 전달
  → 서버: Iamport API 재조회 → 금액 일치 검증
  → 통과 시: 주문 DB 저장 + 결제이력 저장
  → 실패 시: 결제 취소 요청
```

`IamportClient`는 `@Configuration` Bean으로 분리하여 Spring이 라이프사이클을 관리.

### Image — 이미지 저장 방식

`thumbnail/`(상품 대표 이미지)과 `contentImages/`(상품 상세 이미지) 모두 **외부 URL만 DB에 저장**. 파일 자체를 로컬 서버에 저장하지 않음. 실제 이미지 파일은 CDN 또는 외부 스토리지에 위치하며, DB에는 해당 경로 문자열만 보관.

이 방식으로 서버 디스크 의존성 제거, 스케일아웃 시 이미지 동기화 문제 없음.

---

## 인증/인가 아키텍처

### 로그인 방식 2가지

```
사용자
 ├─ POST /formLogin                          → FormLoginSuccessHandler  → JWT 쿠키 발급
 └─ GET  /oauth2/authorization/{kakao|naver} → OAuth2LoginSuccessHandler → JWT 쿠키 발급
```

### JWT 전략

| 토큰 | 저장 위치 | 만료 |
|------|-----------|------|
| Access Token | HttpOnly 쿠키 | 30분 |
| Refresh Token | HttpOnly 쿠키 + JPA DB | 7일 |

- 매 요청 `JWTFilterV3`에서 Access Token 검증 → `SecurityContext` 세팅
- 로그아웃 시 Access Token을 Redis blacklist 등록 (남은 TTL만큼), Refresh Token DB 삭제, 세션 무효화
- `TokenCookieWriter`: `app.secure` 설정 하나로 로컬(`SameSite=Lax`) ↔ 운영(`SameSite=None, Secure=true`) 자동 분기

### SecurityConfig 이중 체인

```
Order 1: apiSecurityFilterChain  →  /api/**
  역할: REST 클라이언트(SPA/모바일)용 JWT 인증
  특징: STATELESS, CSRF 비활성, JSON 401/403 응답

Order 2: webSecurityFilterChain  →  /**
  역할: 브라우저용 세션 + 로그인
  특징: Form Login, OAuth2, CSRF 활성, 리다이렉트 응답
```

두 체인 분리 이유: REST API 클라이언트와 브라우저는 에러 응답 형식(JSON vs 리다이렉트), 세션 전략(Stateless vs IF_REQUIRED) 요구사항이 서로 다르기 때문.

**로그아웃 처리**: Spring Security 내장 `LogoutFilter`를 비활성화하고 `CustomLogoutFilter`가 `POST /logout`을 단독 처리. Spring의 기본 LogoutFilter(order ~900)가 CustomLogoutFilter(order ~1299)보다 먼저 실행되어 JWT 쿠키 초기화 및 Redis 블랙리스트 등록이 되지 않던 버그를 이 방식으로 해결.

### 역할 기반 인가 (@RequiresRole AOP)

Spring Security `@PreAuthorize` 대신 커스텀 `@RequiresRole`을 AOP로 적용. Controller가 아닌 Service 메서드 단위에서 역할 검증.

```java
@RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
public Long createProduct(ProductRequestDto requestDto, ...) { ... }
```

---

## 주요 기술 결정

**Form Login 단일화**  
기존 `/api/login` JSON 엔드포인트를 Form Login으로 대체. `LoginFilter`, `CustomJsonEmailPasswordAuthenticationFilter`, `MemberAuthService` 삭제. Spring Security 표준 흐름 활용으로 코드 단순화.

**TokenCookieWriter — 쿠키 정책 중앙화**  
로그인 성공 핸들러마다 중복되던 쿠키 코드를 `TokenCookieWriter` 하나로 집중. 환경별 분기가 한 곳에서 관리됨.

**GlobalExceptionHandler — 컨트롤러 try-catch 일원화**  
각 컨트롤러에 흩어져 있던 `NoSuchElementException`, `ExistingMemberException` try-catch 블록을 `@RestControllerAdvice`로 일원화. 컨트롤러는 정상 흐름만 작성.

**Redis 활용 전략**

| 용도 | 키 | TTL |
|------|----|-----|
| JWT 블랙리스트 | `blacklist:{token}` | 토큰 남은 만료 시간 |
| 임시 주문 | `order:{memberId}` | 결제 완료 전까지 |
| 프로필 이미지 캐시 | `profileImages::{memberId}` | 60분 |

---

## 실행 방법

**환경 변수 설정**

```bash
IMP_API_KEY=포트원_API_키
IMP_SECRET_KEY=포트원_시크릿_키
```

**로컬 실행 준비**

1. MySQL — `shop` 데이터베이스 생성
2. Redis — `localhost:6379` 실행
3. `application.yml` — `app.secure: false` 확인
4. Spring Profile `local` 로 실행 시 더미 상품 10건 자동 삽입 (기존 더미 데이터는 먼저 삭제 후 재생성 — 멱등 보장)

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
# 기본 포트: http://localhost:8080
```

> `DummyProductInitializer`는 `@Profile("local")`로 보호되어 운영 환경에서는 실행되지 않음.  
> 기동 시 FK 순서(옵션 → 썸네일 → 상품)를 고려한 순서로 기존 더미 데이터를 삭제 후 재생성.

---

## 인증 흐름 상세

### Form Login

```
POST /formLogin
  ↓
UsernamePasswordAuthenticationFilter
  → CustomUserDetailsService.loadUserByUsername()
  → BCrypt 비밀번호 검증
  ↓ 성공
FormLoginSuccessHandler
  → CustomUserDetails에서 memberId, role 추출
  → TokenService.issueLoginTokens()
      ├─ AccessToken 생성 (30분)
      ├─ RefreshToken 생성 (7일)
      └─ RefreshTokenRepository 저장
  → TokenCookieWriter.write() → HttpOnly 쿠키
  → redirect "/"
```

### OAuth2 (Kakao / Naver)

```
GET /oauth2/authorization/{provider}
  ↓
CustomOAuth2UserService.loadUser()
  → provider에서 사용자 정보 조회
  → OAuth2ResponseFactory.create() — 공급자별 응답 파싱
  → OAuth2MemberService.findOrCreateSocialMember()
      ├─ 기존 회원: activate()
      └─ 신규 회원: Member.createSocialMember() + Profile 생성
  ↓
OAuth2LoginSuccessHandler
  → TokenService.issueLoginTokens()
  → TokenCookieWriter.write()
  → redirect "/login?redirectedFromSocialLogin=true"
```

### 토큰 재발급

```
POST /api/v1/reissue  (refreshToken 쿠키 포함)
  ↓
TokenService.reissue()
  → RefreshToken 검증 (만료, DB 존재 여부)
  → 기존 RefreshToken 삭제
  → 새 Access + Refresh 발급 & DB 저장
  → TokenCookieWriter.write()
```

### JWT 검증 (매 요청)

```
/api/** 요청
  ↓
JWTFilterV3
  → 쿠키 또는 Authorization 헤더에서 AccessToken 추출
  → Redis blacklist 확인 → 등록됐으면 403
  → JWTUtil.validateToken() → 만료/서명 검증
  → memberId, role → CustomUserDetails → SecurityContext 세팅
```

---

## 핵심 클래스 역할

| 클래스 | 패키지 | 역할 |
|--------|--------|------|
| `JWTUtil` | `jwts/utils` | JWT 생성, 파싱, 검증 — 순수 유틸 |
| `TokenService` | `jwts/service` | 로그인 토큰 발급, 재발급 비즈니스 로직 |
| `TokenCookieWriter` | `jwts/utils` | Access/Refresh를 HttpOnly 쿠키로 write/clear, 환경별 분기 |
| `TokenResolver` | `jwts/utils` | 쿠키·헤더에서 토큰 문자열 추출 |
| `JWTFilterV3` | `jwts/filter` | 매 요청 JWT 검증 → SecurityContext 세팅 |
| `CustomLogoutFilter` | `jwts/filter` | 로그아웃: Redis 블랙리스트 + RefreshToken 삭제 + 쿠키 초기화 + 세션 무효화 |
| `CustomUserDetails` | `jwts/entity` | Spring Security Principal (memberId, role 보관) |
| `FormLoginSuccessHandler` | `jwts/handler` | 폼 로그인 성공 → 토큰 발급 + 리다이렉트 |
| `OAuth2LoginSuccessHandler` | `oauth2/handler` | OAuth2 성공 → 토큰 발급 + 리다이렉트 |
| `CustomOAuth2UserService` | `oauth2/service` | provider 사용자 정보 조회 + 회원 가입/갱신 |
| `MemberAccountService` | `members/service` | 회원 가입/조회/상태 변경 단일 진입점 |
| `ProductQueryHelper` | `global/queries` | QueryDSL BooleanBuilder 조건 조립 |
| `SecurityConfig` | `config/security` | FilterChain 2개 정의 (API JWT / Web Form+OAuth2) |
| `DummyProductInitializer` | `global/dummy` | 로컬 전용 더미 상품 10건 초기화 (`@Profile("local")`) |
