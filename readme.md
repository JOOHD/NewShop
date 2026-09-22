# JooShop — Spring Boot E-Commerce Platform

> Spring Boot 3 기반 쇼핑몰 백엔드.
> JWT 쿠키 인증, OAuth2 소셜 로그인, DDD Aggregate Root 설계를 중심으로 구현.

**🔗 라이브 데모: [http://3.106.240.183](http://3.106.240.183)** — AWS EC2에 Docker Compose + GitHub Actions CI/CD로 실제 배포되어 있습니다. 직접 회원가입/로그인/장바구니/주문까지 눌러보실 수 있습니다.
> 도메인/HTTPS는 포트폴리오 목적상 의도적으로 생략했습니다 (Elastic IP 미사용이라 인스턴스 재시작 시 IP가 바뀔 수 있습니다 — 접속이 안 되면 알려주세요).

> 이 README는 "코드를 처음 보는 사람이 구조를 빠르게 파악하기 위한" 요약입니다.
> 설계 배경(왜 이렇게 만들었는지), 리팩토링 Before/After, 트러블슈팅 서사는 **[`arrangeFile/portfolio/PROJECT_OVERVIEW.md`](arrangeFile/portfolio/PROJECT_OVERVIEW.md)** 에 자세히 정리되어 있습니다.

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
| **Infra** | Docker, EC2, GitHub Actions CI/CD, Redis(블랙리스트/랭킹/캐싱) |

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
│   ├── exception/               # GlobalExceptionHandler
│   ├── mail/                    # 이메일 인증 서비스
│   ├── image/                   # 이미지 처리 유틸
│   └── dummy/                   # 로컬/운영 공통 더미 데이터 초기화
│
├── members/                    # 회원 도메인 (Aggregate Root: Member)
├── order/                      # 주문 도메인 (Aggregate Root: Orders)
├── product/                    # 상품 도메인 (Aggregate Root: Product)
├── productVariant/             # 상품 옵션(사이즈/성별/색상 조합)
├── payment/                    # 결제 도메인 (Iamport 연동)
├── cart/                       # 장바구니
├── address/                    # 배송 주소
├── profile/                    # 프로필 (이미지, 나이, 성별)
├── Inquiry/                    # 상품 문의 / 답변
├── wishList/                   # 위시리스트
├── categorys/                  # 상품 카테고리
├── thumbnail/                  # 상품 대표 이미지
├── productDetailImages/        # 상품 상세 이미지
└── admin/                      # 관리자 (회원, 상품, 주문 관리)
```

---

## 핵심 설계 원칙 (요약)

- **레이어드 아키텍처, 단방향 의존**: `Controller → Service → Repository`. Service가 타 도메인 정보가 필요할 때도 Repository 직접 참조 대신 해당 도메인 Service를 통해서만 접근.
- **DDD Aggregate Root**: `Member`/`Orders`/`Product`는 Setter 없이 도메인 메서드(`member.ban()`, `order.complete()`)로만 상태 변경. 생성은 정적 팩토리 메서드로만 허용.
- **SecurityFilterChain 이중 분리**: `/api/**`(STATELESS, JWT, JSON 응답)와 `/**`(Form/OAuth2, 세션, 리다이렉트)를 별도 체인으로 분리 — REST 클라이언트와 브라우저의 요구사항이 다르기 때문.
- **DTO 규칙**: 응답 DTO는 `@Getter` + `private final`, 변환은 `static from(Entity)`. `@Data` 사용 안 함(불필요한 Setter 노출 방지).

*(각 원칙을 도입한 이유와 Before/After는 PROJECT_OVERVIEW.md 참고)*

---

## 주요 기능 (요약)

| 도메인 | 핵심 구현 |
|---|---|
| 인증 | JWT(HttpOnly Cookie) + OAuth2(Kakao/Naver) + Redis 블랙리스트 로그아웃 |
| 주문/결제 | Cart → DB 직접 저장(1단계), Iamport 서버 재검증으로 결제 위변조 차단 |
| 상품 | QueryDSL 동적 필터링(할인/추천/카테고리/정렬), Redis ZSet 실시간 조회수 랭킹 |
| 인가 | `@RequiresRole` 커스텀 AOP — Controller가 아닌 Service 메서드 단위 검증 |
| 예외 처리 | `GlobalExceptionHandler`(`@RestControllerAdvice`)로 컨트롤러 try-catch 일원화 |

*(각 기능의 구현 상세/코드/선택 이유는 PROJECT_OVERVIEW.md의 "주요 기능 구현" 섹션 참고)*

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
4. Spring Profile `local`(또는 운영)로 실행 시 더미 상품 10건이 최초 1회 자동 생성됨 (이미 있으면 건드리지 않음 — 운영 주문 데이터 보존을 위해 멱등하게 설계)

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
# 기본 포트: http://localhost:8080
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
| `DummyProductInitializer` | `global/dummy` | 더미 상품 최초 1회 초기화 (로컬/운영 공통, 있으면 스킵) |

---

## 더 읽어보기

| 문서 | 내용 |
|---|---|
| [`arrangeFile/portfolio/PROJECT_OVERVIEW.md`](arrangeFile/portfolio/PROJECT_OVERVIEW.md) | 설계 배경, 주요 기능 구현 상세, 트러블슈팅, 리팩토링 Before/After, Self-review |
| [`arrangeFile/portfolio/INTERVIEW_REFACTORING_STORY.md`](arrangeFile/portfolio/INTERVIEW_REFACTORING_STORY.md) | 면접 대비 — 리팩토링 서사, 예상 질문/답변 |
| [`arrangeFile/project_flow/`](arrangeFile/project_flow) | 로그인/JWT/OAuth2/주문·결제 흐름 등 코드 단위 상세 정리 |
| [`arrangeFile/TROUBLESHOOTING.md`](arrangeFile/TROUBLESHOOTING.md) | 겪은 버그 전체 기록 (문제/원인/해결/러닝포인트) |
