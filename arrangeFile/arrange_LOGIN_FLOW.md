# JooShop 로그인 흐름 & JWT/OAuth2 클래스 정리

> 최종 리팩토링 기준 (2026-06). JSON API 로그인 제거, Form Login + OAuth2 통합.

---

## 로그인 방식 한눈에 보기

```
사용자
 ├─ POST /formLogin (이메일+비밀번호 폼)
 │       └─→ FormLoginSuccessHandler → TokenService → TokenCookieWriter → 쿠키 발급
 │
 ├─ GET /oauth2/authorization/kakao (또는 naver)
 │       └─→ CustomOAuth2UserService → OAuth2LoginSuccessHandler → TokenService → TokenCookieWriter → 쿠키 발급
 │
 └─ 이메일 인증 완료 (신규 가입)
         └─→ EmailVerificationController → TokenService → TokenCookieWriter → 쿠키 발급
```

---

## 1. Form Login 흐름

```
POST /formLogin  (username=이메일, password=비밀번호)
  │
  ▼
[Spring Security UsernamePasswordAuthenticationFilter]
  - UsernamePasswordAuthenticationToken 생성
  - AuthenticationManager.authenticate() 위임
  │
  ▼
[CustomUserDetailsService.loadUserByUsername(email)]
  - MemberAccountService.findMemberByEmail()
  - CustomUserDetails 반환 (memberId, role 포함)
  │
  ▼
[Spring Security] BCrypt 비밀번호 검증
  │
  ├─ 실패 → FormLoginFailureHandler → 401
  │
  └─ 성공
       ▼
      [FormLoginSuccessHandler.onAuthenticationSuccess()]
        - Authentication에서 memberId, role 추출
        - MemberAccountService.findMemberByEmail() → Member 조회
        - TokenService.issueLoginTokens(member, role)
            ├─ JWTUtil.createAccessToken()   → access JWT
            ├─ JWTUtil.createRefreshToken()  → refresh JWT
            └─ RefreshTokenRepository 저장 (기존 있으면 갱신)
        - TokenCookieWriter.write(response, accessToken, refreshToken)
        - redirect → "/"
```

---

## 2. OAuth2 (카카오 / 네이버) 흐름

```
GET /oauth2/authorization/{provider}
  │
  ▼
[Spring Security OAuth2 Client]
  - provider에게 인가 코드 요청 (redirect)
  │
  ▼  (provider 로그인 완료 후)
GET /login/oauth2/code/{provider}?code=xxx
  │
  ▼
[CustomOAuth2UserService.loadUser()]
  - super.loadUser() → provider에서 사용자 정보 조회
  - OAuth2ResponseFactory.create() → 공급자별 파싱
  - SocialLoginCommand 생성 (socialId: "kakao_12345")
  - OAuth2MemberService.findOrCreateSocialMember()
      ├─ socialId로 Member 조회 → 있으면 activate()
      └─ 없으면 Member.registerSocial() + Profile 생성
  - CustomOAuth2User 반환
  │
  ▼
[OAuth2LoginSuccessHandler.onAuthenticationSuccess()]
  - CustomOAuth2User에서 socialId 추출
  - MemberAccountService.findMemberBySocialId() → Member 조회
  - TokenService.issueLoginTokens(member, role)
  - TokenCookieWriter.write(response, accessToken, refreshToken)
  - redirect → "/login?redirectedFromSocialLogin=true"
```

---

## 3. 토큰 재발급 흐름

```
POST /api/v1/reissue  (refreshToken 쿠키 포함)
  │
  ▼
[TokenController.reissue()]
  - TokenResolver.resolveTokenFromCookie("refreshAuthorization")
  - TokenService.reissue(refreshToken)
      ├─ JWTUtil.validateToken() → 만료/타입 검증
      ├─ RefreshTokenRepository 존재 확인
      ├─ 기존 RefreshToken DB 삭제
      └─ 새 Access + Refresh 토큰 발급 & 저장
  - TokenCookieWriter.write() → 새 쿠키 덮어씀
```

---

## 4. 로그아웃 흐름

```
POST /logout  (accessToken, refreshToken 쿠키 포함)
  │
  ▼
[CustomLogoutFilter.doFilter()]  ← JWTFilterV3보다 앞에 등록
  - Cookie에서 accessToken, refreshToken 추출
  - Redis에 "blacklist:{accessToken}" 저장 (남은 만료 시간 TTL)
  - RefreshTokenRepository.deleteByRefreshToken()
  - TokenCookieWriter.clear() → 쿠키 만료 처리
  - 200 OK
```

---

## 5. JWT 검증 흐름 (매 요청)

```
/api/** 요청
  │
  ▼
[JWTFilterV3.doFilterInternal()]
  - Cookie 또는 Authorization Header에서 accessToken 추출
  - Redis blacklist 확인 → 등록된 토큰이면 403
  - JWTUtil.validateToken() → 만료/서명 검증 → 실패 시 401
  - memberId, role 추출 → CustomUserDetails 생성
  - SecurityContextHolder에 Authentication 저장
  - chain.doFilter() 통과

shouldNotFilter() 예외 (JWT 검증 생략):
  /css/**, /js/**, /Images/**
  /login, /api/v1/reissue
  /api/** 가 아닌 모든 웹 요청
```

---

## 6. TokenCookieWriter — 개념

| 구분 | 내용 |
|------|------|
| **역할** | Access/Refresh 토큰을 HttpOnly 쿠키로 응답에 추가 |
| **핵심** | `app.secure: false/true` 하나로 로컬↔운영 쿠키 정책 자동 분기 |
| **로컬** | `SameSite=Lax, Secure=false` |
| **운영** | `SameSite=None, Secure=true` |
| **사용처** | FormLoginSuccessHandler, OAuth2LoginSuccessHandler, EmailVerificationController, CustomLogoutFilter, TokenController |

---

## 7. 클래스 역할 정리

### 인증 핵심 클래스

| 클래스 | 위치 | 역할 한 줄 요약 |
|--------|------|----------------|
| `JWTUtil` | `jwts/utils` | JWT 생성(createAccessToken/createRefreshToken), 파싱, 검증 — 순수 유틸 |
| `TokenService` | `jwts/service` | 로그인 토큰 발급(`issueLoginTokens`), 재발급(`reissue`) — 비즈니스 로직 |
| `TokenCookieWriter` | `jwts/utils` | Access/Refresh 토큰을 HttpOnly 쿠키로 write/clear — 환경별 분기 포함 |
| `TokenResolver` | `jwts/utils` | 요청에서 쿠키·헤더로부터 토큰 문자열 추출 |
| `JWTFilterV3` | `jwts/filter` | 매 요청 JWT 검증 → SecurityContext 세팅 |
| `CustomLogoutFilter` | `jwts/filter` | 로그아웃 처리: Redis 블랙리스트 + DB 삭제 + 쿠키 초기화 |
| `CustomUserDetails` | `jwts/entity` | Spring Security Principal — memberId, role 보관 |
| `CustomUserDetailsService` | `jwts/service` | email → Member 조회 → CustomUserDetails 반환 (Form Login용) |

### Form Login 핸들러

| 클래스 | 역할 한 줄 요약 |
|--------|----------------|
| `FormLoginSuccessHandler` | 폼 로그인 성공 시 토큰 발급 + 쿠키 → 리다이렉트 |
| `FormLoginFailureHandler` | 폼 로그인 실패 시 401 응답 |

### OAuth2 클래스

| 클래스 | 역할 한 줄 요약 |
|--------|----------------|
| `CustomOAuth2UserService` | provider 사용자 정보 조회 + 회원 가입/갱신 |
| `OAuth2ResponseFactory` | Kakao/Naver provider 응답을 OAuth2Response 인터페이스로 파싱 |
| `OAuth2MemberService` | socialId 기반 회원 조회 or 신규 생성 |
| `OAuth2LoginSuccessHandler` | OAuth2 로그인 성공 시 토큰 발급 + 쿠키 → 리다이렉트 |
| `OAuth2LoginFailureHandler` | OAuth2 로그인 실패 시 처리 |

### 인프라 / 설정

| 클래스 | 역할 한 줄 요약 |
|--------|----------------|
| `SecurityConfig` | FilterChain 2개 정의: apiSecurityFilterChain(JWT), webSecurityFilterChain(Form+OAuth2) |
| `FilterFactory` | `JWTFilterV3` 인스턴스 생성 전담 |
| `RefreshToken` (Entity) | DB 저장 Refresh 토큰 — memberId, tokenValue, expirationDateTime |
| `RefreshTokenRepository` | JPA: Refresh 토큰 저장/조회/삭제 |

---

## 8. SecurityConfig 구조

```
Order 1: apiSecurityFilterChain  → /api/**
  - STATELESS, CSRF 비활성
  - JWTFilterV3 등록
  - PUBLIC_API, PUBLIC_GET_API → permitAll
  - USER/SELLER/ADMIN 역할별 제어

Order 2: webSecurityFilterChain  → /**
  - CSRF 활성 (쿠키 기반)
  - JWTFilterV3 등록
  - CustomLogoutFilter 등록 (POST /logout)
  - Form Login: POST /formLogin → FormLoginSuccessHandler
  - OAuth2 Login: /oauth2/** → OAuth2LoginSuccessHandler
```

---

## 9. 주요 설정값 (application.yml)

```yaml
spring:
  jwt:
    secret: "..."                        # JWT 서명 키 (Base64)
    refresh-expiration-seconds: 604800   # Refresh 유효기간 (7일)

app:
  secure: false   # 로컬: false | 운영: true (TokenCookieWriter 분기)
```

**Access Token 만료** — `JWTUtil` 내 상수: `60 * 30` (30분)  
**Refresh Token 만료** — yml `spring.jwt.refresh-expiration-seconds` (7일)

---

## 10. 리팩토링 완료 내역

| 항목 | 조치 |
|------|------|
| `LoginFilter` | **삭제** (JSON API 로그인 불필요) |
| `CustomJsonEmailPasswordAuthenticationFilter` | **삭제** |
| `MemberAuthService` | **삭제** (호출처 없음) |
| `OAuth2TokenService` | **삭제** → `TokenService`로 통합 |
| `JWTUtil @Value` 경로 오류 | `${jwt.refresh-...}` → `${spring.jwt.refresh-...}` |
| `GlobalExceptionHandler` ExistingMember 파라미터 타입 | `EmailAlreadyExistsException` → `ExistingMemberException` |
| `GlobalExceptionHandler` NoSuchElementException | 401 오류 메시지 → 404 + `e.getMessage()` |
| `EmailVerificationController` 3파라미터 컴파일 에러 | `TokenService + TokenCookieWriter` 적용 |
| `members/entity/CertificationEntity` 중복 Entity | `@Entity` 제거 (레거시 마킹) |
| `ProductApiControllerV1.updateProduct()` | `@RequestBody` → `@RequestPart` (멀티파트 수정) |
| `CartService`, `OrderService` 레이어 위반 | `MemberRepository` → `MemberAccountService` |
| `ProductApiControllerV1.objectMapper` | `public` → `private` |
