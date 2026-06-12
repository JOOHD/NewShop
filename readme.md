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
| 외부 직접 생성 금지 | `@NoArgsConstructor(access = PROTECTED)`, `@AllArgsConstructor` 제거 |
| Setter 노출 금지 | `@Setter` 제거, 상태 변경은 의미 있는 도메인 메서드로 |
| 팩토리 메서드 | `Member.registerGeneral()`, `Orders.createOrder()`, `Product.create()` |
| 상태 변경 캡슐화 | `member.ban()`, `member.activate()`, `order.complete()` 등 |

### DTO 설계 원칙

| 유형 | 어노테이션 | 이유 |
|------|------------|------|
| 요청 DTO (Request) | `@Getter` only | 서버에서 필드를 바꿀 이유 없음 |
| 응답 DTO (Response) | `@Getter` only | 읽기 전용 |
| 변환 메서드 | `static from(Entity)` | `toEntity()`는 DTO가 Entity를 생성한다는 오해를 유발 |

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
│   └── image/                  # 이미지 처리 유틸
│
├── members/                    # 회원 도메인 (Aggregate Root: Member)
│   ├── entity/Member           # 회원 상태, 역할, 도메인 메서드
│   └── service/MemberAccountService   # 회원 가입/조회/상태관리 단일 진입점
│
├── order/                      # 주문 도메인 (Aggregate Root: Orders)
│   ├── entity/Orders           # 주문 생성, 상태 변경 도메인 메서드
│   └── service/OrderService    # 주문 생성/조회/임시저장(Redis)
│
├── product/                    # 상품 도메인 (Aggregate Root: Product)
│   ├── entity/Product          # 상품 등록, 수정, 옵션 관리 도메인 메서드
│   └── service/ProductServiceV1 # 상품 CRUD, 검색, 랭킹
│
├── payment/                    # 결제 도메인
│   ├── service/PaymentService  # Iamport 결제 검증, 취소
│   └── entity/PaymentHistory   # 결제 이력
│
├── cart/                       # 장바구니
├── address/                    # 배송 주소
├── profiile/                   # 프로필 (이미지, 나이, 성별)
├── Inquiry/                    # 상품 문의 / 답변
├── wishList/                   # 위시리스트
├── categorys/                  # 상품 카테고리
├── productManagement/          # 상품 옵션/재고 관리
├── thumbnail/                  # 상품 대표 이미지
├── contentImages/              # 상품 상세 이미지
└── admin/                      # 관리자 (회원, 상품, 주문 관리)
```

---

## 인증/인가 아키텍처

### 로그인 방식 3가지

```
사용자
 ├─ POST /formLogin                          → FormLoginSuccessHandler  → JWT 쿠키 발급
 ├─ GET  /oauth2/authorization/{kakao|naver} → OAuth2LoginSuccessHandler → JWT 쿠키 발급
 └─ 이메일 인증 완료 (신규 가입)              → EmailVerificationController → JWT 쿠키 발급
```

### JWT 전략

| 토큰 | 저장 위치 | 만료 |
|------|-----------|------|
| Access Token | HttpOnly 쿠키 | 30분 |
| Refresh Token | HttpOnly 쿠키 + JPA DB | 7일 |

- 매 요청 `JWTFilterV3`에서 Access Token 검증 → `SecurityContext` 세팅
- 로그아웃 시 Access Token을 Redis blacklist 등록 (남은 TTL만큼), Refresh Token DB 삭제
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

**IamportConfig — 결제 클라이언트 Bean 분리**  
컨트롤러에서 `@PostConstruct`로 직접 초기화하던 `IamportClient`를 `@Configuration` Bean으로 분리. 라이프사이클을 Spring이 관리.

**GlobalExceptionHandler — 컨트롤러 try-catch 일원화**  
각 컨트롤러에 흩어져 있던 `NoSuchElementException`, `ExistingMemberException` try-catch 블록을 `@RestControllerAdvice`로 일원화. 컨트롤러는 정상 흐름만 작성.

**Redis 활용 전략**

| 용도 | TTL |
|------|-----|
| JWT 블랙리스트 `blacklist:{token}` | 토큰 남은 만료 시간 |
| 임시 주문 `order:{memberId}` | 결제 완료 전까지 |
| 프로필 이미지 캐시 `profileImages::{memberId}` | 60분 |

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

```bash
./gradlew bootRun
# 기본 포트: http://localhost:8080
```

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
| `CustomLogoutFilter` | `jwts/filter` | 로그아웃: Redis 블랙리스트 + RefreshToken 삭제 + 쿠키 초기화 |
| `CustomUserDetails` | `jwts/entity` | Spring Security Principal (memberId, role 보관) |
| `FormLoginSuccessHandler` | `jwts/handler` | 폼 로그인 성공 → 토큰 발급 + 리다이렉트 |
| `OAuth2LoginSuccessHandler` | `oauth2/handler` | OAuth2 성공 → 토큰 발급 + 리다이렉트 |
| `CustomOAuth2UserService` | `oauth2/service` | provider 사용자 정보 조회 + 회원 가입/갱신 |
| `MemberAccountService` | `members/service` | 회원 가입/조회/상태 변경 단일 진입점 |
| `SecurityConfig` | `config/security` | FilterChain 2개 정의 (API JWT / Web Form+OAuth2) |
