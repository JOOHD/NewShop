# Directory 설명 및 정리

JWT 디렉토리
- 인증 기능 자체를 수행하는 영역
- 토큰 생성, 검증, 재발급, 인증 객체 생성

Config 디렉토리
- 인증 기능이 동작하도록 스프링 환경을 조립하는 영역
- SecurityFilterChain, CORS, Redis, QueryDSL, Bean 등록

## JWT 전체 구조

[요청 흐름]
Login → Filter → Service → JWT 생성 → 응답

[API 요청]
요청 → JWTFilter → Token 검증 → 인증 객체 생성

[재발급]
Controller → TokenService → JWT 재발급

## 핵심 개념 4개

| 영역                  | 역할                      |
| ------------------- | ----------------------- |
| **Filter**          | 요청 가로채서 인증 처리           |
| **Service**         | 토큰 생성/저장/재발급 (비즈니스 로직)  |
| **Util**            | JWT 생성/파싱/쿠키/토큰 추출 (도구) |
| **DTO/UserDetails** | 인증 정보 전달 객체             |

## 클래스 한 줄 요약 (핵심만)

LoginFilter
- JSON 로그인 성공/실패 흐름을 처리하고, 토큰 발급은 TokenService에 위임합니다.

JWTFilterV3
- 요청의 AccessToken을 검증하고, SecurityContext에 인증 객체를 저장합니다.

CustomLogoutFilter
- 로그아웃 시 AccessToken을 Redis 블랙리스트에 등록하고 RefreshToken을 삭제합니다.

TokenService
- AccessToken/RefreshToken 발급, 재발급, RefreshToken 저장·갱신을 담당합니다.

JWTUtil
- JWT 생성, 검증, Claim 파싱만 담당하는 순수 유틸 클래스입니다.

TokenResolver
- Authorization Header 또는 Cookie에서 토큰 문자열만 추출합니다.

CookieUtil
- 인증 쿠키 생성, 조회, 삭제를 담당합니다.

CustomUserDetailsService
- 로그인 시 Member를 조회하고 Spring Security용 UserDetails로 변환합니다.

CustomMemberDto
- Member 엔티티를 인증 전용 Snapshot 데이터로 변환합니다.

CustomUserDetails
- SecurityContext에 저장될 인증 사용자 객체입니다.

TokenController
- RefreshToken 기반 재발급 요청을 받고 TokenService에 위임합니다.

FormLoginSuccessHandler
- Form Login 성공 시 JWT 발급과 쿠키 저장을 처리합니다.

FormLoginFailureHandler
- Form Login 실패 시 JSON 에러 응답을 반환합니다.

### 중요 핵심 

👉 JWTUtil = 계산기 (절대 비즈니스 로직 없음)
👉 TokenService = JWT의 진짜 주인 (발급/재발급/저장)
👉 Filter = 흐름만 제어 (절대 로직 많이 넣지 마)

- JWT = 인증 영역
👉 절대 Member Aggregate를 직접 건드리지 않는다
👉 DTO / Snapshot으로 끊는다

### JWT 리팩토링 스크립트

기존에는 LoginFilter, JWTFilter, TokenController 안에 토큰 생성, 검증, RefreshToken 저장 로직이 섞여 있었다.

그래서 리팩토링 과정에서 Filter는 요청 흐름 제어만 담당하게 하고, JWT 발급/재발급/저장은 TokenService로 분리.

또한 JWTUtil은 JWT 생성/파싱/검증만 담당하도록 제한, DB 조회나 비즈니스 로직은 넣지 않았다.

인증된 사용자 정보는 Member 엔티티를 직접 SecurityContext에 넣지 않고,
CustomMemberDto 와 CustomUserDetails로 변환해서 인증 계층과 도메인 계층을 분리

### 요약

- JWT 리팩토링의 핵심은 인증 인프라 코드가 Member 도메인을 직접 오염시키지 않도록 경계를 분리한 것입니다.

- Member는 회원 도메인의 Aggregate Root이고,
JWT 계층은 인증을 위한 Snapshot DTO만 사용합니다.

- 그래서 SecurityContext에는 Member 엔티티가 아니라 CustomUserDetails를 저장하고,
토큰 발급/재발급 같은 인증 로직은 TokenService로 집중시켰습니다.

## Config 개념

- Config = 조립
- FilterFactory = 필터 생성
- SecurityConfig = 보안 정책
- CorsConfig = 프론트 연결 허용
- RedisConfig = Redis 연결
- WebConfig = MVC/정적 리소스
- QueryDslConfig = QueryDSL 사용 준비

## 클래스 한 줄 요약

SecurityConfig
- Spring Security 필터 체인, 인증/인가 정책, 로그인/로그아웃/JWT 필터 등록을 담당합니다.

CorsConfig
- 프론트엔드와 백엔드 간 CORS 허용 정책을 설정합니다.

WebConfig
- 정적 리소스 경로, 인터셉터, MVC 관련 설정을 담당합니다.

RedisConfig
- RedisConnectionFactory, RedisTemplate 등 Redis 사용 환경을 구성합니다.

QueryDslConfig
- JPAQueryFactory Bean을 등록해 QueryDSL 사용 환경을 구성합니다.

MailConfig
- 이메일 발송에 필요한 JavaMailSender 설정을 담당합니다.

OAuth2Config
- OAuth2 로그인 관련 provider/client 설정과 성공/실패 흐름 연결을 담당합니다.

PasswordEncoderConfig
- BCryptPasswordEncoder 같은 비밀번호 암호화 Bean을 등록합니다.

ObjectMapperConfig
- JSON 직렬화/역직렬화 정책을 전역으로 설정합니다.

FilterFactory
- SecurityConfig가 직접 필터를 new 하지 않도록 JWT/Login/Logout 필터 생성을 담당합니다.

### 요약

기존에는 SecurityConfig가 너무 많은 객체를 직접 생성하고 있어서 설정 클래스가 복잡 

그래서 필터 생성 책임은 FilterFactory로 분리하고, SecurityConfig는 보안 정챗과 필터 체인 조립만 담당

Config 클래스들은 비즈니스 로직을 가지지 않고, Spring Bean과 외부 인프라 설정을 조립하는 역할만 하도록 정리