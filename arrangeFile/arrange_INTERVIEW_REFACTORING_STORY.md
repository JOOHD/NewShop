# JooShop 리팩토링 핵심 정리 — 면접 대비

> 카테고리별 "왜 바꿨는지 (면접 답변)" + "Before/After (뭐가 좋아졌는지)" + 성장 서사.
> 코드/커밋 근거는 실제 프로젝트(git log, arrangeFile/*.md)에서 확인한 내용만 담았다.

---

## 전체 한눈에 보기

| 카테고리 | Before | After | 핵심 이유 |
|---|---|---|---|
| Entity 생성 | `@Builder` | private 생성자 + 검증 + 정적 팩토리 | Builder 크래시 → 캡슐화·검증 강화 |
| 인증(JWT) | Filter가 토큰 생성/저장까지 담당 | Filter는 흐름만, TokenService가 발급/재발급 전담 | 계층 오염 방지 |
| 로그인 | JSON API 로그인 + Form Login 공존 | Form Login 단일화 | 불필요한 커스텀 필터 유지비용 제거 |
| OAuth2 통신 | `RestTemplate` 매번 new | `WebClient` Bean 재사용 | deprecated 제거, 에러 선언적 처리 |
| 주문 | Redis 임시저장 3단계 | Cart→DB 직접 저장 1단계 | 일반 쇼핑몰엔 불필요한 복잡도 제거 |
| 예외 처리 | Controller마다 try-catch | GlobalExceptionHandler 일원화 | 응답 포맷 통일 (다음 단계: BusinessException 통합 설계 완료) |

---

## 1. DDD Aggregate Root — Entity 생성 방식 3단계 진화 ⭐

가장 서사가 뚜렷한 부분. **버그를 계기로 설계 원칙을 이해하게 된 과정**이라 면접에서 "왜 이 구조를 선택했나"에 가장 좋은 답이 된다.

### 1단계 (초기) — `@Builder`

```java
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
public class Member { ... }

// 사용
Member.builder()
    .email(email)
    .username(username)
    .memberRole(role)
    .build();
```

가독성은 좋았지만 JPA 엔티티에서 `@Builder` + `@AllArgsConstructor` 조합이 **런타임 크래시**를 일으켰다 (커밋 `aa9332c`, "jpa entity builder crash").

### 2단계 — 정적 팩토리로 전환 (버그 대응, 급한 불부터)

```java
public static Member createSocialMember(String email, String username, ...) {
    Member member = new Member();
    member.email = email;
    member.username = username;
    member.certifiedByEmail = true;
    member.active = true;
    return member;
}
```

Builder는 없앴지만 필드를 직접 대입하는 방식이라 **검증 로직이 없고, 캡슐화가 약했다.** (Setter는 없앴는데 필드 직접 대입은 사실상 같은 문제)

### 3단계 (현재, 26/04) — 검증 포함 private 생성자 + 명명된 정적 팩토리 + Aggregate 보호

```java
private Member(String email, String password, String username, String nickname,
               String phoneNumber, MemberRole memberRole, SocialType socialType,
               String socialId, boolean certifiedByEmail, boolean admin) {
    this.email = requireText(email, "이메일은 필수입니다.");
    this.password = requireNonNull(password, "비밀번호는 필수입니다.");
    // ... 필드마다 검증
    this.active = true;
    this.joinedAt = LocalDateTime.now();
}

public static Member registerGeneral(String email, String encodedPassword, String username,
                                      String nickname, String phoneNumber, String socialId) {
    return new Member(email, requireText(encodedPassword, "..."), username, nickname,
                       phoneNumber, MemberRole.USER, SocialType.GENERAL, socialId, false, false);
}
// registerAdmin(), registerSocial() 동일 패턴

// Aggregate Root 보호 — 하위 엔티티도 반드시 Member를 거쳐서만 연결
public void attachProfile(Profiles profile) {
    if (profile == null) throw new IllegalArgumentException("프로필은 null일 수 없습니다.");
    this.profile = profile;
    profile.attachTo(this);   // 양방향 연관관계도 여기서 함께 처리
}
```

### 면접용 답변

> "처음엔 `@Builder`로 가독성 좋게 객체를 생성했는데, JPA 엔티티에 `@Builder`와 `@AllArgsConstructor`를 같이 쓰면서 런타임 오류가 발생했습니다. 급한 대로 정적 팩토리 메서드로 바꿨는데, 이번엔 필드를 직접 대입하는 방식이라 검증 로직이 없다는 문제를 알게 됐습니다. 최종적으로는 생성자를 private으로 감추고 그 안에서 `requireText`, `requireNonNull` 같은 검증을 거치게 한 뒤, `registerGeneral()`, `registerAdmin()`, `registerSocial()`처럼 의도가 드러나는 이름의 정적 팩토리로 외부에 노출했습니다. 그리고 하위 엔티티(Profile)와의 연결도 `attachProfile()`처럼 Member를 거치도록 강제해서, Member가 회원 도메인의 Aggregate Root로서 자기 상태를 스스로 지키게 만들었습니다."

### Before → After 한 줄 비교

| 항목 | Before | After |
|---|---|---|
| 생성 방식 | `Member.builder()...build()` | `Member.registerGeneral(...)` |
| 필드 검증 | 없음 | 생성자 내부에서 `requireText`/`requireNonNull` |
| 파라미터 순서 문제 | Builder 체이닝으로 순서 무관 | 정적 팩토리 메서드명으로 의도 명확 + 검증까지 겸함 |
| 하위 엔티티 연결 | 외부에서 직접 필드 접근 가능 | `attachProfile()`로 Member만 통제 |
| 목적 | 가독성 | 가독성 + 무결성 + 캡슐화 |

---

## 2. 인증 — JWT/OAuth2가 Member 도메인을 직접 건드리지 않도록 계층 분리

### Before

`LoginFilter`, `JWTFilter`에 토큰 생성·검증·저장 로직이 섞여 있었고, `SecurityContext`에 `Member` 엔티티를 직접 저장.

### After

```
Filter        → 요청 흐름 제어만
TokenService  → AccessToken/RefreshToken 발급·재발급·저장 (비즈니스 로직)
JWTUtil       → 생성/파싱/검증만 (순수 유틸, DB 접근 없음)
SecurityContext → Member 엔티티 대신 CustomUserDetails(Snapshot DTO) 저장
```

### 면접용 답변

> "초기엔 Filter 클래스 하나가 토큰 생성부터 저장까지 다 처리해서 책임이 몰려 있었습니다. 이걸 Filter(흐름 제어), TokenService(발급/재발급 비즈니스 로직), JWTUtil(순수 생성/검증 유틸)로 나눴고, SecurityContext에는 Member 엔티티를 직접 넣지 않고 CustomUserDetails라는 인증 전용 스냅샷 객체를 넣도록 바꿨습니다. 인증이라는 인프라 영역이 회원 도메인을 직접 오염시키지 않도록 경계를 그은 겁니다."

**뭐가 좋아졌나**: 인증 로직 변경 시 도메인(Member) 코드를 안 건드려도 됨. 테스트 작성 시 Member 전체를 몰라도 CustomUserDetails만으로 인증 로직 테스트 가능.

---

## 3. 로그인 — Form Login 단일화 + CustomLogoutFilter 순서 버그

### Before → After

| 항목 | Before | After |
|---|---|---|
| 로그인 방식 | `/api/login`(JSON, LoginFilter) + Form Login 공존 | Form Login 단일화 |
| 삭제된 클래스 | — | `LoginFilter`, `CustomJsonEmailPasswordAuthenticationFilter`, `MemberAuthService` |
| 로그아웃 | Spring 내장 `LogoutFilter` 사용 | `CustomLogoutFilter` 단독 처리 |

**로그아웃 버그**: Spring 기본 `LogoutFilter`(order ~900)가 커스텀 필터(order ~1299)보다 먼저 실행되어, Redis 블랙리스트 등록·쿠키 초기화가 되기 전에 로그아웃이 먼저 끝나버리는 순서 문제가 있었다. 내장 필터를 끄고 `CustomLogoutFilter`가 `/logout`을 전담하도록 바꿔서 해결.

### 면접용 답변

> "브라우저 기반 쇼핑몰인데 JSON API 로그인과 Form Login이 같이 있어서, Spring Security 표준 흐름을 우회하는 커스텀 필터를 계속 유지해야 하는 비용이 컸습니다. JSON 로그인 관련 클래스 3개를 삭제하고 Form Login으로 단일화했습니다. 이 과정에서 로그아웃 시 Redis 블랙리스트 등록이 실제 로그아웃 완료보다 늦게 실행되는 필터 순서 버그도 같이 발견해서, Spring 내장 LogoutFilter를 끄고 커스텀 필터가 전담하도록 고쳤습니다."

---

## 4. OAuth2 — `RestTemplate` → `WebClient`

### Before

```java
RestTemplate restTemplate = new RestTemplate();  // 매번 new, 커넥션 풀 없음
ResponseEntity<String> response = restTemplate.exchange(KAKAO_TOKEN_URI, HttpMethod.POST, request, String.class);
return parseTokenResponse(response.getBody());   // 수동 파싱, 에러 처리 없음
```

### After

```java
@Bean
public WebClient webClient() { return WebClient.builder()...build(); }  // Bean 재사용

return webClient.post()
    .uri(KAKAO_TOKEN_URI)
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError, r -> r.bodyToMono(String.class)
        .map(body -> new IllegalStateException("카카오 토큰 요청 실패: " + body)))
    .bodyToMono(OAuthTokenResponse.class)
    .block();
```

### 면접용 답변

> "카카오 토큰 요청에 RestTemplate을 매번 새로 생성해서 쓰고 있었는데, Spring 6부터 deprecated 상태이기도 하고 커넥션 풀 재사용이 안 되는 문제가 있었습니다. WebClient를 Bean으로 등록해서 재사용하도록 바꾸고, `.onStatus()`로 4xx/5xx 에러를 선언적으로 분리 처리하게 했습니다. 나중에 WebFlux로 완전히 넘어가도 `.block()`만 제거하면 되는 구조라 확장성도 챙겼습니다."

---

## 5. 주문/결제 — Redis 2단계 주문 제거 + 트랜잭션 전략 정리

### Before → After

```
Before: POST /order/create(Redis 임시저장) → GET /order/temp(조회) → POST /order/confirm(DB 저장)
After : POST /order/confirm 하나로 → Cart 조회 → DB 직접 저장
```

**이유**: 클론 코딩 원본 구조를 그대로 따라가다 보니 일반 쇼핑몰에는 불필요한 Redis 중간 단계가 남아있었음. 실제로 결제 실패/이탈 시 Redis TTL로 자연 소멸시키는 정도로 충분하다고 판단해 단순화.

**결제 검증**: 클라이언트가 보낸 결제 금액을 그대로 믿지 않고, `imp_uid`로 Iamport 서버에 재조회해서 실제 결제 금액과 비교 — 클라이언트 위변조 차단.

**트랜잭션 정리**: Checked Exception(`IOException`, `IamportResponseException`)이 발생하는 결제 취소 로직은 `rollbackFor = Exception.class`를 붙이는 대신 **try-catch로 RuntimeException으로 감싸서** 계층을 오염시키지 않는 방식을 택함.

### 면접용 답변

> "주문 확정 흐름이 Redis 임시저장 → 조회 → DB 저장 3단계로 나뉘어 있었는데, 일반 쇼핑몰 트래픽 규모에선 이 정도 복잡도가 필요 없다고 판단해서 Cart 조회 후 바로 DB에 저장하는 구조로 단순화했습니다. 결제 쪽은 클라이언트가 보낸 금액을 그대로 믿지 않고 Iamport 서버에 직접 재조회해서 검증하도록 했고, 체크 예외가 발생하는 결제 취소 로직은 `rollbackFor` 설정 대신 try-catch로 RuntimeException으로 변환해서 트랜잭션 어노테이션을 단순하게 유지했습니다."

---

## 6. 예외 처리 — GlobalExceptionHandler 일원화 (+ 다음 단계 설계)

### 완료된 부분 (Before → After)

각 Controller에 흩어져 있던 try-catch 블록을 `@ControllerAdvice` 기반 `GlobalExceptionHandler` 하나로 모으고, `ErrorResponse` 공통 포맷(status/error/message)으로 응답 형식을 통일.

### 아직 적용 전 — 설계는 끝난 다음 단계

현재 `GlobalExceptionHandler`는 예외 클래스마다 `@ExceptionHandler`가 하나씩 있는 상태(약 15개). 다음 리팩토링으로 설계해둔 구조:

```java
// ErrorCode enum — 상태코드+도메인코드+메시지를 한 곳에
MEMBER_NOT_FOUND(404, "M001", "회원을 찾을 수 없습니다.")

// BusinessException — 모든 커스텀 예외의 부모
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
}

// 예외 15개 각각의 핸들러 대신 딱 하나
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handle(BusinessException ex) {
    ErrorCode code = ex.getErrorCode();
    return ResponseEntity.status(code.getStatus()).body(ErrorResponse.from(code));
}
```

추가로 `JWTFilterV3`, `SecurityConfig`의 인증 예외 응답 포맷도 지금은 각기 달라서(`{"error":..}` vs `{"message":..}`), 같은 `ErrorCode`/`ErrorResponse`로 통일하는 것까지 설계에 포함됨.

### 면접용 답변

> "지금은 예외 클래스마다 `@ExceptionHandler`를 하나씩 두고 있어서, 새 예외가 추가될 때마다 핸들러도 같이 늘어나는 구조입니다. 이걸 개선하기 위해 `ErrorCode` enum에 상태코드·도메인코드·메시지를 모아두고, 모든 커스텀 예외가 `BusinessException`이라는 공통 부모를 상속하도록 설계했습니다. 그러면 핸들러 하나(`@ExceptionHandler(BusinessException.class)`)로 15개 예외를 전부 처리할 수 있고, 새 예외를 추가할 때도 `ErrorCode`에 값 하나 추가하고 `BusinessException`을 상속하기만 하면 됩니다. 인증 필터(JWTFilterV3)와 SecurityConfig의 예외 응답 포맷이 서로 달랐던 것도 이 구조로 통일할 계획입니다."

---

## 정리 — 이 리팩토링들의 공통 흐름

전반적으로 "일단 동작하게 만든다" → "버그/중복을 계기로 원인을 파고든다" → "계층 간 책임을 분리하고 도메인을 보호하는 구조로 수렴한다"는 패턴이 반복됨. Member 엔티티의 `@Builder → 수동 정적 팩토리 → 검증 포함 정적 팩토리`가 이 패턴을 가장 압축적으로 보여주는 사례.
