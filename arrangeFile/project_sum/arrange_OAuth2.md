# OAuth2 Directory 설명 및 정리

## OAuth2 전체 흐름

외부 Provider 로그인 성공
→ 사용자 정보 조회
→ 우리 서비스 Member로 연결
→ JWT 발급
→ Cookie 저장
→ 이후 요청은 JWTFilterV3가 인증 처리

## OAuth2 핵심 요약

1. OAuth2는 외부 인증이다.
- 카카오/네이버/구글이 사용자 인증을 대신 해준다.

2. 내 프로젝트는 provider 사용자 정보를 Member로 변환
- provider + providerId 기준으로 Member 조회 or 가입

3. 로그인 성공 후에는 JWT 발급
- OAuth2SuccessHandler + TokenService + CookieUtil

4. 이후 요청은 OAuth2가 아니라 JWTFilterV3가 처리한다.
- AccessToken cookie + JWTFilterV3 + SecurityContextHolder

5. 일반 로그인과 OAuth2 로그인은 최종적으로 같은 인증 체계로 합쳐져야 한다.
- 일반 로그인 성공 → JWT 발급
- OAuth2 로그인 성공 → JWT 발급

## OAuth2 클래스별 한 줄 요약

| 클래스                       | 핵심 역할                                                                    |
| ------------------------- | ------------------------------------------------------------------------ |
| `SecurityConfig`          | OAuth2 로그인, JWT 필터, 로그인/로그아웃 필터를 Security Filter Chain에 연결하는 설정 클래스      |
| `CustomOAuth2UserService` | Provider에서 받은 사용자 정보를 우리 서비스 회원 정보로 변환하는 OAuth2 사용자 로딩 서비스               |
| `OAuth2UserInfo`          | Provider별 사용자 정보를 공통 메서드로 다루기 위한 인터페이스                                   |
| `KakaoOAuth2UserInfo`     | 카카오 응답 JSON에서 id, email, nickname, profile image를 추출하는 클래스               |
| `NaverOAuth2UserInfo`     | 네이버 응답 JSON의 `response` 내부에서 사용자 정보를 추출하는 클래스                            |
| `GoogleOAuth2UserInfo`    | 구글 응답 JSON에서 `sub`, email, name, picture를 추출하는 클래스                       |
| `OAuth2UserInfoFactory`   | `registrationId` 값에 따라 Kakao/Naver/Google UserInfo 객체를 생성하는 팩토리          |
| `OAuth2MemberService`     | OAuth2 사용자 정보를 기준으로 기존 회원을 조회하거나 신규 회원을 생성하는 서비스                         |
| `CustomOAuth2User`        | OAuth2 인증 성공 후 Spring Security가 보관할 커스텀 사용자 객체                           |
| `OAuth2SuccessHandler`    | OAuth2 로그인 성공 후 JWT를 발급하고 쿠키에 저장한 뒤 프론트로 리다이렉트하는 클래스                     |
| `OAuth2FailureHandler`    | OAuth2 로그인 실패 시 실패 응답 또는 실패 페이지로 리다이렉트하는 클래스                             |
| `TokenService`            | AccessToken/RefreshToken을 발급하고 RefreshToken을 저장/갱신하는 JWT 서비스             |
| `JWTUtil`                 | JWT 생성, 검증, 만료 확인, claim 추출을 담당하는 유틸                                     |
| `CookieUtil`              | AccessToken/RefreshToken 쿠키 생성, 조회, 삭제를 담당하는 유틸                          |
| `TokenResolver`           | 요청의 Cookie 또는 Header에서 JWT를 추출하는 유틸                                      |
| `JWTFilterV3`             | 요청마다 JWT를 검증하고 인증 객체를 SecurityContext에 저장하는 필터                           |
| `LoginFilter`             | 일반 로그인 요청에서 email/password를 검증하고 JWT를 발급하는 필터                            |
| `CustomLogoutFilter`      | 로그아웃 시 AccessToken 블랙리스트 등록, RefreshToken 삭제, 쿠키 삭제를 처리하는 필터             |
| `FilterFactory`           | JWTFilter/LoginFilter/LogoutFilter 생성 책임을 모아 SecurityConfig를 가볍게 만드는 팩토리 |


## OAuth2 + JWT 흐름

[브라우저]
    |
    | 1. /oauth2/authorization/kakao
    v
[Spring Security OAuth2 Client]
    |
    | 2. 카카오 로그인 페이지로 redirect
    v
[Kakao]
    |
    | 3. 로그인 성공 후 /login/oauth2/code/kakao?code=... 로 callback
    v
[Spring Security]
    |
    | 4. code로 access token 요청
    | 5. access token으로 사용자 정보 요청
    v
[CustomOAuth2UserService]
    |
    | 6. provider 사용자 정보 파싱
    | 7. Member 조회 or 가입
    v
[OAuth2SuccessHandler]
    |
    | 8. TokenService로 JWT 발급
    | 9. CookieUtil로 쿠키 저장
    | 10. 프론트로 redirect
    v
[브라우저]
    |
    | 11. 이후 API 요청마다 accessToken 쿠키 포함
    v
[JWTFilterV3]
    |
    | 12. JWT 검증
    | 13. SecurityContextHolder 인증 저장
    v
[Controller]

## 1. 전체 인증 구조에서 OAuth2의 위치

[일반 로그인]
LoginFilter
→ AuthenticationManager
→ MemberService / UserDetailsService
→ TokenService
→ CookieUtil
→ AccessToken / RefreshToken 발급


[OAuth2 로그인]
OAuth2 Provider Login
→ CustomOAuth2UserService
→ OAuth2SuccessHandler
→ TokenService
→ CookieUtil
→ AccessToken / RefreshToken 발급


[공통 인증 처리]
JWTFilterV3
→ Cookie / Header에서 AccessToken 추출
→ JWTUtil 검증
→ Member 조회
→ SecurityContextHolder에 인증 객체 저장

[핵심]
- 일반 로그인과 OAuth2 로그인은 로그인 진입 경로만 다르고, 
- 성공 이후에는 TokenService + JWTFilterV3 구조를 공유한다.

## 3. OAuth2 로그인 전체 흐름

### 3-1. 사용자가 OAuth2 로그인 버튼 클릭
 
- 예를 들어 프론트에서 카카오 로그인 버튼을 누르면 보통 이런 URL로 이동한다.

/oauth2/authorization/kakao
/oauth2/authorization/naver
/oauth2/authorization/google
    - 이 URL은 직접 만든 컨트롤러가 아니라, Spring Security OAuth2 Client가 처리하는 기본 엔드포인트야.

### 3-2. Spring Security가 외부 로그인 페이지로 redirect

spring:
// oauth2 관련 설정
security:
    oauth2:
        client:
            registration:
                naver:
                    client-name: naver
                    client-id: vLS6M_CoERVps6WZ0irD
                    client-secret: YY4ZB7l5Jq
                    redirect-uri: http://localhost:8080/login/oauth2/code/naver
                    authorization-grant-type: authorization_code
                    scope: name,email
                kakao:
                    client-name: kakao
                    client-id: b31f22ff817f06ceab9fb711f1c2aaad
                    client-secret: l8kB2cvK1853FBAxe5f3IUOHFKCxG3AQ
                    client-authentication-method: client_secret_post
                    redirect-uri: http://localhost:8080/login/oauth2/code/kakao
                    authorization-grant-type: authorization_code
                    scope:
                    - profile_nickname
                    - account_email
                google:
                    client-name: google
                    client-id: ${GOOGLE_CLIENT_ID}
                    client-secret: ${GOOGLE_CLIENT_SECRET}
                    redirect-uri: ${GOOGLE_REDIRECT_URI}
                    authorization-grant-type: authorization_code
                    scope: profile,email
            provider:
                naver:
                      authorization-uri: https://nid.naver.com/oauth2.0/authorize
                      token-uri: https://nid.naver.com/oauth2.0/token
                      user-info-uri: https://openapi.naver.com/v1/nid/me
                      user-name-attribute: response
                kakao:
                      authorization-uri: https://kauth.kakao.com/oauth/authorize
                      token-uri: https://kauth.kakao.com/oauth/token
                      user-info-uri: https://kapi.kakao.com/v2/user/me
                      user-name-attribute: id

- 중요한 설정은 redirect-uri.
- 외부 로그인 성공 후, 카카오가 이 주소로 다시 돌려보낸다.

### 3-3. kakao/naver/google authorization code 반환

- 외부 로그인 성공 후, provider는 우리 서버로 이런 식의 요청을 보낸다.
- /login/oauth2/code/kakao?code=abc123...
  - 여기서 code는 아직 사용자 정보가 아니고, 
  - security가 내부적으로 이 code를 가지고, provider에 다시 요청해서,
  - access token을 받고 그 받은 토큰으로 사용자 정보를 조회 한다.

### 3-4. CustomOAuth2UserService 실행

- 외부 provider에서 사용자 정보를 받아오면, 그 다음 실행되는 클래스는 보통 이거다.
- CustomOAuth2UserService
  - 외부 provider에서 받은 사용자 정보를
  - 우리 서비스에서 쓸 수 있는 OAuth2User 객체로 변환한다.

```java
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberOAuth2Service memberOAuth2Service;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest
                .getClientRegistration()
                .getRegistrationId();

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.of(
                registrationId,
                oauth2User.getAttributes()
        );

        Member member = memberOAuth2Service.findOrCreate(userInfo);

        return CustomOAuth2User.from(member, oauth2User.getAttributes());
    }
}
```
1. super.loadUser(userRequest)
   → provider에서 사용자 정보 가져옴
2. registrationId 확인
   → kakao / naver / google 구분
3. OAuth2UserInfoFactory로 provider별 응답 구조 통일
4. MemberOAuth2Service에서 회원 조회 or 신규 가입
5. CustomOAuth2User로 감싸서 Spring Security에 반환

## 4. OAuth2UserInfo 역할

[목적]
- oauth2가 어려운 이유는 카카오, 네이버, 구글 사용자 정보 응답 구조가 다 다르다.

```json
[
  {
    "sub": "12345",
    "email": "test@gmail.com",
    "name": "홍길동",
    "picture": "..."
  },
  {
    "id": 12345,
    "kakao_account": {
      "email": "test@kakao.com",
      "profile": {
        "nickname": "홍길동",
        "profile_image_url": "..."
      }
    }
  },
  {
    "response": {
      "id": "abc123",
      "email": "test@naver.com",
      "name": "홍길동",
      "profile_image": "..."
    }
  }
]
```
- 각기 다른 응답으로 인해 provider 별 클래스를 나눔
  OAuth2UserInfo
  ├─ KakaoOAuth2UserInfo
  ├─ NaverOAuth2UserInfo
  └─ GoogleOAuth2UserInfo

- provider 마다 다른 json 구조를 쓰기 쉬운 공통 메서드로 통일
```java
public interface OAuth2UserInfo {
    String getProvider();
    String getProviderId();
    String getEmail();
    String getName();
    String getProfileImageUrl();
}
```

## 5. OAuth2UserInfoFactory 

[목적]
- provider 종류에 따라 알맞은 구현체를 만들어준다.

```java
public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {}

    public static OAuth2UserInfo of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "kakao" -> new KakaoOAuth2UserInfo(attributes);
            case "naver" -> new NaverOAuth2UserInfo(attributes);
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 OAuth2 provider입니다.");
        };
    }
}
```
- registrationId 로 kaka/google/naverOAuth2UserInfo 생성

## 6. MemberOAuth2Service / OAuth2MemberService

[목적]
- 이 두 서비스 클래스가 가장 중요
- 외부 OAuth2 사용자 정보를 우리 Member 엔티티와 연결한다.

[역할]
1. provider + providerId로 기존 회원 조회
2. 없으면 email로 기존 일반 가입 회원이 있는지 확인
3. 정책에 따라 계정 연결 or 예외 or 신규 OAuth2 회원 생성
4. Member 반환

## 7. CustomOAuth2User

[목적]
- OAuth2 인증 성공 후 Spring Security가 사용할 사용자 객체

[핵심]
- OAuth2 로그인 성공 사용자에게 JWT를 발급하고 쿠키에 저장한다.
- OAuth2SuccessHandler는 “OAuth2 로그인 성공 후 JWT 인증 체계로 진입시키는 다리”다.

- security는 oauth2 로그인 성공 후, oauth2 타입을 SecurityContext에 넣으려 한다.
- 그런데 provider에서 받은 기본 OAuth2User는 우리 Member 정보를 잘 모른다.
- 그래서 보통 커스텀 클래스를 만든다.

## 8. OAuth2SuccessHandler/failer

- SecurityConfig 에 등록
- CustomOAuth2UserService 가 회원을 조회/가입시키고, 
- CustomOAuth2User 를 반환하면,
- 로그인 성공 후, OAuth2SuccessHandler/failer 가 실행된다.

## 9. TokenService & OAuth2 관계

[핵심]
1. Access Token 생성
2. Refresh Token 생성
3. RefreshToken 저장 또는 갱신
4. TokenResponse 반환

- LoginFilter
→ tokenService.issueToken(member)

- OAuth2SuccessHandler
→ tokenService.issueToken(member)

## 10. CookieUtil & OAuth2 관계

- CookieUtil
- AccessToken / RefreshToken을 쿠키로 저장, 조회, 삭제하는 공통 유틸

CookieUtil.addSecureCookie(response, "accessToken", accessToken, accessMaxAge);
CookieUtil.addSecureCookie(response, "refreshToken", refreshToken, refreshMaxAge);

[차이점]
Local:
    Secure=false
    SameSite=Lax

Production:
    Secure=true
    SameSite=None

- OAuth2는 리다이렉트 기반이라 쿠키 SameSite 정책이 꽤 중요하다.

## 11. JWTFilterV3 & OAuth2 관계

- OAuth2 로그인은 최초 로그인 성공 시에만, OAuth2 관련 클래스들이 작동한다.
- 그 이후에는 oauth2 클래스가 거의 관여 x

- 사용자가 카카오 로그인 성공 후, 쿠키를 받은 상태

[브라우저 요청]
GET /api/v1/members/me 
Cookie: accessToken=eyJ...

JWTFilterV3
→ Cookie에서 accessToken 추출
→ JWTUtil로 검증
→ memberId/email/role 추출
→ Member 조회
→ Authentication 생성
→ SecurityContextHolder에 저장

[핵심]
- 일반 로그인으로 받은 JWT든
- 카카오 로그인으로 받은 JWT든
- 검증 방식은 동일하다.

## 12. SecurityConfig 에서 OAuth2 관련 설정

```java
public SecurityFilterChain webSecurityFilterChain(
        HttpSecurity http,
        RefreshTokenRepository refreshTokenRepository,
        ObjectMapper objectMapper
) throws Exception {

    JWTFilterV3 jwtFilter = filterFactory.createJWTFilter();
    CustomLogoutFilter customLogoutFilter =
            new CustomLogoutFilter(jwtUtil, redisTemplate, refreshTokenRepository, objectMapper);

    http
    .formLogin(form -> form
            .loginPage("/login")
            .loginProcessingUrl("/formLogin")
            .usernameParameter("username")
            .passwordParameter("password")
            .successHandler(formLoginSuccessHandler)
            .failureHandler(formLoginFailureHandler)
            .permitAll()
    )        
    .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService)
                )
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
    );

    // JWT 필터는 따로 등록
    http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    http.addFilterBefore(customLogoutFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```
| 설정                                     | 의미                                 |
| -------------------------------------- | ---------------------------------- |
| `oauth2Login()`                        | OAuth2 로그인 기능 활성화                  |
| `userInfoEndpoint()`                   | provider에서 사용자 정보 가져온 뒤 처리할 서비스 지정 |
| `userService(customOAuth2UserService)` | 사용자 정보 변환/회원 조회/회원가입 처리            |
| `successHandler(oAuth2SuccessHandler)` | 로그인 성공 후 JWT 발급                    |
| `failureHandler(...)`                  | 로그인 실패 처리                          |


## 13. OAuth2 로그인 vs Form 일반 로그인

[일반 로그인]

1. 사용자가 email/password 입력
2. LoginFilter가 요청 가로챔
3. AuthenticationManager가 검증
4. 성공하면 LoginFilter successfulAuthentication 실행
5. TokenService가 JWT 발급
6. CookieUtil이 쿠키 저장

[OAuth2 로그인]

1. 사용자가 카카오/네이버/구글 로그인 클릭
2. Spring Security가 provider 로그인 페이지로 이동
3. provider 로그인 성공
4. Spring Security가 사용자 정보 조회
5. CustomOAuth2UserService 실행
6. Member 조회 or 가입
7. OAuth2SuccessHandler 실행
8. TokenService가 JWT 발급
9. CookieUtil이 쿠키 저장

[공통점]
- 최종 결과는 둘 다 JWT 쿠키 발급

[차이점]
- 일반 로그인은 password 검증
- OAuth2 로그인은 provider가 인증을 대신 해줌

## 14. kakao/naver/google 클래스별 특징

[OAuth2UserInfo]
- provider 사용자 정보 구조를 공통 인터페이스로 통일

[KakaoOAuth2UserInfo]
- 카카오는 중첩 map 구조라 캐스팅이 많다.
  Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
  Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

[NaverOAuth2UserInfo]
-네이버 응답의 response 내부에서 사용자 정보 추출
여기 안에 id, email, name 등이 있다.
Map<String, Object> response = (Map<String, Object>) attributes.get("response");

[GoogleOAuth2UserInfo]
- 구글 응답에서 sub, email, name, picture 추출 (비교적 단순)
  attributes.get("sub")
  attributes.get("email")
  attributes.get("name")
  attributes.get("picture")

## 15. 헷갈림 정리

1. OAuth2 로그인인데 왜 JWT를 발급함?

- OAuth2는 외부 로그인 인증 방식이다.
- 하지만 내 프로젝트는 내부 API 인증은 JWT 기반
  
OAuth2로 “누군지 확인”
  → 우리 서비스 JWT로 “앞으로 인증 유지”

2. OAuth2 access token과 우리 JWT access token은 같은 건가?

| 구분                           | 의미                         |
| ---------------------------- | -------------------------- |
| OAuth2 Provider Access Token | 카카오/네이버/구글 API를 호출하기 위한 토큰 |
| NewShop JWT Access Token     | NewShop API 인증을 위한 토큰      |

- 내 프로젝트에서는 보통 provider access token을 저장하지 않음.

3. OAuth2 로그인 후 JWTFilterV3는 언제부터 작동함?

- OAuth2 로그인 callback 과정에서는 Spring Security OAuth2 필터들이 주로 작동한다.
- JWTFilterV3는 이후 API 요청부터 중요해진다.

OAuth2 로그인 성공
→ JWT 쿠키 발급
→ 다음 요청부터 JWTFilterV3가 쿠키 읽고 인증 처리

4. OAuth2 회원도 RefreshToken 저장해야 함?

- 내 서비스에서 JWT 인증을 쓰는 이상 OAuth2 회원도 RefreshToken 저장 해야 된다.
- 그래야 access token 만료 시 재발급 구조가 동일하게 돌아간다.

---

## 16. KakaoOAuthClient — RestTemplate → WebClient 리팩토링

### Before (RestTemplate — deprecated)

```java
// 매번 new → 커넥션 풀 없음 / 에러 처리 없음
RestTemplate restTemplate = new RestTemplate();
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
ResponseEntity<String> response = restTemplate.exchange(KAKAO_TOKEN_URI, HttpMethod.POST, request, String.class);
return parseTokenResponse(response.getBody());   // 수동 파싱
```

### After (WebClient — Bean 재사용, 에러 선언적 처리)

```java
// WebClientConfig에서 Bean으로 등록 → 커넥션 풀 재사용
@Bean
public WebClient webClient() {
    return WebClient.builder()
            .codecs(config -> config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
}

// KakaoOAuthClient — Bean 주입
return webClient.post()
        .uri(KAKAO_TOKEN_URI)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .bodyValue(params)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, response ->
                response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException("카카오 토큰 요청 실패 (4xx): " + body))
        )
        .onStatus(HttpStatusCode::is5xxServerError, response ->
                response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException("카카오 서버 오류 (5xx): " + body))
        )
        .bodyToMono(OAuthTokenResponse.class)  // JSON → DTO 자동 역직렬화
        .block();                               // MVC 환경에서 동기 처리
```

### 리팩토링 이유 요약

```
RestTemplate → 동기 Blocking, Spring 6에서 deprecated (maintenance mode)
WebClient   → 비동기 Non-Blocking, .onStatus()로 4xx/5xx 에러 분리 처리
             Bean 등록으로 커넥션 풀 재사용
             추후 WebFlux 전환 시 .block()만 제거하면 됨

.block() 쓰는 이유: 현재 Spring MVC 환경 → 동기처럼 결과 받아야 함
                    비동기 전환 시 Mono<OAuthTokenResponse> 반환으로 교체
```