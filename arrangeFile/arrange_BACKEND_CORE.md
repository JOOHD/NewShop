# Java / Spring / DB / JPA 핵심 개념 정리

> 객체 → Spring → JPA → 트랜잭션 → 보안 → 실무 패턴까지.
> "왜"를 중심으로 정리했다.

---

## 🗂️ 파트 구성

| 파트 | 범위 | 섹션 |
|---|---|---|
| **PART 1** | Java 기초 — 클래스/인스턴스/JVM | 1 ~ 10 |
| **PART 2** | Spring Core — Bean/DI/IoC/AOP | 11 ~ 17 |
| **PART 3** | JPA / DB — 영속성/트랜잭션/쿼리 | 18 ~ 29 |
| **PART 4** | 보안 / 인증 — JWT/OAuth2/Session | 30 ~ 36 |
| **PART 5** | 실무 심화 — Thread/GC/SOLID/WebClient | 37 ~ 46 |
| **PART 6** | 면접 심화 — JPA/보안/인프라/예외 | 47 ~ 65 |
| **PART 7** | 심화 실전 — 예외/트랜잭션/컬렉션/WebClient | 66 ~ 76 |

---

## 목차

1. [Class / Instance / Bean](#1-class--instance--bean)
2. [Spring의 핵심 역할 — IoC / DI](#2-spring의-핵심-역할--ioc--di)
3. [ApplicationContext = Spring Container](#3-applicationcontext--spring-container)
4. [Bean 등록 방법](#4-bean-등록-방법)
5. [싱글톤 — 하나를 나눠 쓰는 이유](#5-싱글톤--하나를-나눠-쓰는-이유)
6. [생성자 주입이 표준인 이유](#6-생성자-주입이-표준인-이유)
7. [객체를 중요시하는 이유](#7-객체를-중요시하는-이유)
8. [new 구조 분해 — 생성자 실행 흐름](#8-new-구조-분해--생성자-실행-흐름)
9. [JPA 객체 복원 — DB row → Java 객체](#9-jpa-객체-복원--db-row--java-객체)
10. [Entity 생성자 2종류](#10-entity-생성자-2종류)
11. [DTO vs Entity — 섞으면 안 되는 이유](#11-dto-vs-entity--섞으면-안-되는-이유)
12. [전체 흐름 — 요청에서 DB까지](#12-전체-흐름--요청에서-db까지)
13. [@Service/@Controller vs new — 객체를 다루는 두 가지 방식](#13-servicecontroller-vs-new--객체를-다루는-두-가지-방식)
14. [이스터에그 개념들 — 본질을 뒤집는 것들](#14-이스터에그-개념들--본질을-뒤집는-것들)
15. [JVM 메모리 구조 — Stack / Heap / Method Area](#15-jvm-메모리-구조--stack--heap--method-area)
16. [접근제어자 — private / protected / public / (package)](#16-접근제어자--private--protected--public--package)
17. [인터페이스 vs 상속 — 헷갈리는 두 개념](#17-인터페이스-vs-상속--헷갈리는-두-개념)

---

## 1. Class / Instance / Bean

```
Class    = 설계도 (메모리에 없음)
Instance = new로 만든 실제 객체 (메모리에 올라간 것)
Bean     = Spring이 만들고 관리하는 Instance
```

```java
// Class — 설계도만 존재
public class Member { ... }

// Instance — 개발자가 직접 new로 생성
Member member = new Member("a@b.com", "1234");

// Bean — Spring이 new를 대신 해주고 Container에 보관
@Service
public class MemberService { ... }
```

셋 다 "객체"지만 범위가 다르다.

```
Class → (new) → Instance → (Spring이 관리하면) → Bean
```

---

## 2. Spring의 핵심 역할 — IoC / DI

Spring 없이는 개발자가 객체 생성과 연결을 직접 관리해야 했다:

```java
// Spring 없이 — 수십 개 객체를 순서 맞춰 직접 생성
MemberRepository repo    = new MemberRepository(dataSource);
PasswordEncoder  encoder = new BCryptPasswordEncoder();
MemberService    service = new MemberService(repo, encoder);
OrderService     orders  = new OrderService(service, repo);
// 하나 바뀌면 연결된 모든 곳 수정
```

Spring이 이걸 대신 해준다. 개발자는 "이 클래스가 필요해"만 선언하면 된다.

이걸 **IoC(제어의 역전)** 라고 한다. 객체 생성/관리의 제어권이 개발자 → Spring으로 역전.
그 방법이 **DI(의존성 주입)** — Spring이 필요한 객체를 생성자에 넣어준다.

**Spring Framework vs Spring Boot:**

- **Spring Framework (2004)** — IoC/DI 이미 있었음. 근데 XML 설정이 수백 줄 → 복잡
- **Spring MVC** — Spring 안의 웹 계층 프레임워크
- **Spring Boot (2014)** — XML 설정 제거, 자동 설정, 내장 서버. `new` 문제는 이미 Spring이 해결했고, Boot는 그 Spring을 쓰기 편하게 만든 것

---

## 3. ApplicationContext = Spring Container

**Spring Context = Bean들을 담아두는 컨테이너 그 자체.**
`ApplicationContext`는 그 컨테이너의 인터페이스 이름이다. "Spring Context"와 "ApplicationContext"는 같은 말.

```
ApplicationContext (Spring Container)
├── MemberService Bean
├── OrderService Bean
├── JWTUtil Bean
├── RedisTemplate Bean
└── ...
```

앱이 시작될 때 Spring이 이 창고를 만들면서 Bean들을 전부 채워 넣는다.
이 시점이 **"Spring Context 초기화"**.

---

## 4. Bean 등록 방법

```java
// 방법 1 — 어노테이션 (가장 흔함)
@Service        // 서비스 계층
@Repository     // DB 계층
@Controller     // 웹 계층 (View 반환)
@RestController // 웹 계층 (JSON 반환)
@Component      // 위 셋이 내부적으로 포함하는 기본 어노테이션

// 방법 2 — @Configuration + @Bean (외부 라이브러리)
@Configuration
public class IamportConfig {
    @Bean  // 이 메서드가 반환하는 객체를 Bean으로 등록
    public IamportClient iamportClient() {
        return new IamportClient(apiKey, secret); // 직접 수정 못 하는 외부 클래스
    }
}
```

어노테이션들은 사실 기능 차이가 거의 없다. "이 클래스가 어떤 역할인지" 의미를 전달하는 등록 신청서다.
Bean의 실체는 그냥 **Java 객체**다. Spring이 만들고 관리하는 Java 객체.

---

## 5. 싱글톤 — 하나를 나눠 쓰는 이유

Spring Bean은 기본적으로 **싱글톤**이다. 앱 전체에서 딱 하나만 만들어진다.

```java
// 싱글톤 아닐 때 — 요청마다 새 객체 생성
요청 1 → new MemberService() → 처리 → 버림
요청 2 → new MemberService() → 처리 → 버림
// 1000개 요청 = 1000개 객체 생성/소멸 = 메모리/GC 낭비

// 싱글톤 (Spring 기본) — 하나를 계속 재사용
앱 시작 → MemberService 딱 하나 생성
요청 1, 2, 3 ... → 같은 MemberService 사용
```

**왜 안전한가:** Bean에 각 요청의 데이터가 저장되지 않기 때문이다.

```java
@Service
public class MemberService {
    private final MemberRepository memberRepository; // 의존 객체만 있음

    // 요청 데이터는 메서드 파라미터로 받고, 지역변수로 처리하고, return으로 반환
    // 지역변수는 스레드마다 별도 공간 → 공유해도 섞일 일 없음
    public Member findById(Long id) {
        return memberRepository.findById(id).orElseThrow();
    }
}
```

Bean에 인스턴스 변수로 요청 데이터를 저장하면 여러 사용자 데이터가 섞인다.
Bean은 반드시 **무상태(stateless)** 여야 한다. 이게 싱글톤이 안전한 전제 조건이다.

---

## 6. 생성자 주입이 표준인 이유

```java
// 방법 1 — 필드 주입 (편하지만 문제 있음)
@Autowired
private MemberService memberService;

// 방법 2 — 생성자 주입 (표준)
private final MemberService memberService;
public OrderService(MemberService memberService) {
    this.memberService = memberService;
}
// @RequiredArgsConstructor가 이 생성자를 자동 생성
```

생성자 주입이 표준인 이유 3가지:

```java
// ① final 사용 가능 → 주입 후 변경 불가 보장
private final MemberService memberService; // 중간에 null로 바뀔 수 없음

// ② 순환 참조를 앱 시작 시점에 잡아줌
// A → B → A 순환이면 앱 기동 자체가 실패 (빠른 발견)
// 필드 주입은 기동은 되고 런타임에 터짐

// ③ Spring 없이 테스트 가능
OrderService service = new OrderService(mockMemberService); // 직접 넣을 수 있음
// 필드 주입이면 Spring 없이 Mock을 넣을 방법이 없음
```

---

## 7. 객체를 중요시하는 이유

Java가 객체를 중심에 놓는 건 **현실 세계를 코드로 모델링**하기 위해서다.

```java
// 데이터만 있는 구조체 방식 (C언어 스타일)
String email     = "a@a.com";
boolean isBanned = false;
// 이 데이터들이 "같은 회원 것"이라는 보장이 코드에 없음

// 객체 방식 — 데이터 + 행동이 하나로
public class Member {
    private String email;
    private boolean banned;

    public void ban()      { this.banned = true; }  // 이 메서드로만 상태 변경 가능
    public void activate() { this.banned = false; }
}
// "이 데이터는 이 행동으로만 바꿀 수 있다"는 규칙이 코드에 있음
```

Spring이 객체 관리를 중요시하는 건 **객체들 사이의 의존 관계가 복잡하기 때문**이다.
객체가 10개면 직접 관리 가능, 1000개면 불가능. Spring이 그 복잡성을 대신 관리한다.

---

## 8. new 구조 분해 — 생성자 실행 흐름

```java
Member member = new Member("abcd@efgh.com", 1234);
```

```
Member    member    =    new Member("abcd@efgh.com", 1234)
  │          │                │              │
타입       변수명           객체 생성        생성자에 전달하는 값
```

클래스가 이렇다면:

```java
public class Member {
    private String email;
    private int    password;

    public Member(String email, int password) {
        this.email    = email;    // 객체 필드 = 파라미터 값
        this.password = password;
    }
}
```

`new Member("abcd@efgh.com", 1234)` 실행 시:

```
"abcd@efgh.com" → 생성자 파라미터 email    → this.email    = "abcd@efgh.com"
1234            → 생성자 파라미터 password → this.password = 1234
```

`this.email`은 **객체의 필드**, 오른쪽 `email`은 **생성자로 들어온 파라미터**. 이름이 같으므로 `this`로 구분한다.

---

## 9. JPA 객체 복원 — DB row → Java 객체

DB에 이렇게 저장돼 있다:

```
id  | email            | password
1   | abcd@efgh.com    | 1234
```

`memberRepository.findById(1L)` 호출 시 JPA가 내부적으로:

```java
// JPA가 대신 해주는 것 (의사코드)
Member member = new Member(); // 기본 생성자로 빈 객체 생성
member.id       = 1L;
member.email    = "abcd@efgh.com";
member.password = "1234";
```

**DB의 흩어진 컬럼값 → Java 객체 하나로 재조립.** 이게 객체 복원이다.
묶여있지 않은 상품들을 다시 상자에 담아 포장하는 것과 같다.

---

## 10. Entity 생성자 2종류

### ① JPA용 기본 생성자

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Member { ... }
```

JPA가 DB row를 읽어 객체로 복원할 때 쓰는 통로.
`PROTECTED`로 막는 이유: JPA는 쓸 수 있게, 외부에서 `new Member()` 남용은 차단.

### ② 개발자가 쓰는 생성 생성자 (정적 팩토리 메서드)

```java
public static Member registerGeneral(String email, String encodedPw, String nickname) {
    return new Member(email, encodedPw, nickname);
}

private Member(String email, String password, String nickname) {
    this.email    = email;
    this.password = password;
    this.nickname = nickname;
}
```

생성자를 `private`으로 막고 팩토리 메서드로 감싸면:
- 생성 규칙을 강제할 수 있다
- 이름(`registerGeneral`, `registerAdmin`)으로 의도가 드러난다
- 잘못된 상태의 객체 생성을 코드 레벨에서 차단한다

---

## 11. DTO vs Entity — 섞으면 안 되는 이유

Entity 생성자는 **값**을 받는 거지, DTO 자체를 받는 게 아니다.

```java
// ❌ Entity가 DTO를 직접 아는 구조
public Member(JoinRequest request) {
    this.email = request.getEmail(); // Entity가 API 계층을 앎 → 계층 침범
}

// ✅ Service에서 값을 꺼내서 전달
public void join(JoinRequest request) {
    Member member = Member.registerGeneral(
        request.getEmail(),
        passwordEncoder.encode(request.getPassword()),
        request.getNickname()
    );
    memberRepository.save(member);
}
```

| | DTO | Entity |
|---|---|---|
| 역할 | API 요청/응답 임시 포장지 | 도메인 핵심 객체 |
| 위치 | Controller ↔ Service 사이 | Service ↔ DB 사이 |
| 변경 기준 | API 스펙이 바뀌면 | 도메인 규칙이 바뀌면 |

둘을 강하게 묶으면 API 스펙이 바뀔 때 Entity까지 바꿔야 하는 문제가 생긴다.

---

## 12. 전체 흐름 — 요청에서 DB까지

```
클라이언트 JSON
    ↓
@RequestBody JoinRequest (DTO) — API 계층에서 받음
    ↓
Service — DTO 값을 꺼내서 가공 (비밀번호 인코딩 등)
    ↓
Member.registerGeneral(email, encodedPw, nickname)
    ↓
private Member(...) 생성자 실행 → this.email = email ...
    ↓
Member Entity 완성
    ↓
memberRepository.save(member) → DB 저장

--- 이후 조회 시 ---

memberRepository.findById(1L)
    ↓
JPA가 DB row 읽음
    ↓
기본 생성자로 빈 객체 생성 → 필드 채움
    ↓
Member 객체 반환
```

---

## 13. @Service/@Controller vs new — 객체를 다루는 두 가지 방식

Spring에서 객체는 크게 두 종류로 나뉜다:

```
① 애플리케이션 구성 객체 — @Service, @Controller, @Repository
② 도메인/데이터 객체    — Member, Orders, DTO 등
```

### ① 구성 객체 — Spring이 관리

```java
@Service
public class MemberService { ... }

@Controller
public class MemberController { ... }
```

- 앱 전체에서 **딱 하나**만 있으면 됨 (싱글톤)
- 앱 시작 시 한 번 만들어지고 **끝날 때까지 유지**
- 개발자가 `new` 할 이유가 없음 → Spring이 알아서 생성/주입
- 이것들이 **Bean**

### ② 도메인/데이터 객체 — 직접 new (또는 팩토리 메서드)

```java
Member member = Member.registerGeneral(...); // 회원가입마다 새 객체
Orders order  = Orders.createOrder(...);     // 주문마다 새 객체
JoinRequest dto = new JoinRequest(...);      // 요청마다 새 DTO
```

- 요청/상황마다 **다른 데이터**를 담아야 함
- 싱글톤으로 공유하면 안 됨 (데이터가 섞임)
- Spring이 관리할 이유가 없음 → 직접 `new`

### 구분 기준

```
"앱의 뼈대 역할, 하나면 충분한 것"       → @Service/@Controller → Spring 관리
"데이터를 담는 것, 매번 새로 필요한 것"   → Entity/DTO          → new 직접
```

클래스의 **역할과 수명**이 `new` vs 어노테이션을 가르는 기준이다.

---

## 14. 이스터에그 개념들 — 본질을 뒤집는 것들

### ① `@Transactional`은 네 코드에 없다

Spring이 런타임에 네 클래스를 **몰래 상속한 프록시 클래스**를 만들어 Bean으로 등록한다.

```java
// Spring이 실제로 등록하는 것 (의사코드)
class MemberService$$SpringProxy extends MemberService {
    @Override
    public Member registerMember(...) {
        트랜잭션_시작();
        try {
            return super.registerMember(...); // 네 코드
        } catch (Exception e) {
            롤백(); throw e;
        } finally {
            커밋();
        }
    }
}
```

이 때문에 **같은 클래스 내부에서 `@Transactional` 메서드를 호출하면 트랜잭션이 없다**.

```java
@Service
public class OrderService {
    @Transactional
    public void createOrder() { ... }

    public void process() {
        createOrder(); // ❌ 트랜잭션 안 걸림 — 프록시를 거치지 않음
    }
}
```

### ② `final`인데 값이 바뀐다

```java
final List<String> list = new ArrayList<>();
list.add("바뀜");       // ✅ 정상 동작
list = new ArrayList<>(); // ❌ 컴파일 에러
```

`final`은 **참조(주소값)가 고정**되는 거지, 내용물이 고정되는 게 아니다.
집 주소가 고정된 것이지, 집 안의 가구는 바꿀 수 있는 것과 같다.

### ③ `static final`에 Spring이 주입 못 하는 이유

```
JVM 기동
  → ② 클래스 로딩 — static 필드 초기화 (Spring 아직 없음)
  → ③ main() 실행
      → ⑥ Spring Context 생성 — DI 발생
```

`static final`은 ②번에 이미 `null`로 확정된다. Spring DI는 ⑥번에 일어난다.
Spring이 주입하러 왔을 때 이미 `final`이라 변경 불가. 개입할 타이밍 자체가 없다.

> **"Spring은 인스턴스를 관리하는데, `static`은 인스턴스가 아닌 클래스 소속이라 Spring이 손댈 수 없다"**

### ④ Lazy Loading — 객체가 있는데 필드가 null이다

```java
Orders order = orderRepository.findById(1L).get(); // order 객체 존재
order.getMember().getEmail(); // 💥 LazyInitializationException
```

JPA는 `fetch = LAZY`일 때 연관 객체를 **가짜 프록시**로 채워놓는다.
실제 DB 조회는 그 필드에 **처음 접근하는 순간** 일어난다.
트랜잭션이 닫힌 뒤에 접근하면 DB 조회를 못 해서 예외가 터진다. N+1 문제가 여기서 나온다.

### ⑤ `protected` 생성자 — JPA에 구멍을 내준 것

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member { }
```

JPA는 DB에서 데이터를 꺼낼 때 **리플렉션으로 기본 생성자를 호출**해서 객체를 만든다.
`public`으로 열면 누구나 `new Member()`로 빈 객체를 만들 수 있어서 위험하다.
`PROTECTED`는 JPA(같은 패키지/상속)는 접근 가능하고, 외부 코드는 접근 불가 — JPA한테만 몰래 구멍을 내주는 것이다.

### ⑥ `new`를 숨기는 이유 — 정적 팩토리 메서드

```java
// ❌ new 직접 사용
Member member = new Member("email", "pw", USER, GENERAL, null, false, false);
// 파라미터 순서 실수해도 컴파일은 됨 / 어떤 종류의 Member인지 의미 없음

// ✅ 정적 팩토리 메서드
Member member = Member.registerGeneral(email, encodedPw, username, nickname, phone, socialId);
Member admin  = Member.registerAdmin(email, encodedPw, username, nickname, phone, socialId);
// 이름만 봐도 의도가 드러남 / 내부 검증 포함 / 잘못된 상태 생성 불가
```

이 개념들이 전부 같은 질문을 하고 있다:
> **"이 권한/책임이 정말 여기 있어야 하는가?"**

---

## 15. JVM 메모리 구조 — Stack / Heap / Method Area

JVM은 실행 중 데이터를 세 영역으로 나눠 저장한다.

```
┌─────────────────────────────────────────────────────────┐
│                     JVM 메모리                           │
│                                                         │
│  ┌──────────────────┐  ┌──────────────────┐             │
│  │   Method Area    │  │      Heap        │             │
│  │  (앱 전체 공유)   │  │  (앱 전체 공유)   │             │
│  │                  │  │                  │             │
│  │ - 클래스 정보     │  │ - new로 만든 객체 │             │
│  │ - static 변수    │  │ - Spring Bean    │             │
│  │ - static final  │  │ - Entity, DTO    │             │
│  │                  │  │                  │             │
│  │ 앱 시작 시 고정   │  │ GC가 관리/정리   │             │
│  └──────────────────┘  └──────────────────┘             │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ← 스레드마다 독립          │
│  │ Stack A  │  │ Stack B  │                             │
│  │ (요청 A) │  │ (요청 B) │                             │
│  │          │  │          │                             │
│  │ memberId │  │ memberId │  ← 같은 이름이지만          │
│  │    = 1   │  │    = 7   │    별개의 공간              │
│  │ result.. │  │ result.. │                             │
│  └──────────┘  └──────────┘                             │
└─────────────────────────────────────────────────────────┘
```

### 각 영역 역할

**Method Area — 클래스 로딩 시 채워지고 앱 전체가 공유**

```java
public class TokenCookieWriter {
    // 여기 있는 것들이 Method Area에 저장됨
    private static final int ACCESS_MAX_AGE = 60 * 30;   // static final 상수
    private static final String ACCESS_COOKIE_NAME = "accessToken";

    // 클래스 정보(설계도) 자체도 Method Area에 있음
}
```

Spring DI가 `static final`에 주입 못 하는 이유: 클래스 로딩(②) 때 이미 초기화 완료 → Spring DI(⑥)가 도착했을 땐 이미 늦음.

**Heap — new 할 때 생성, 공유, GC가 정리**

```java
// 이것들이 전부 Heap에 올라감
new MemberService()          // Spring Bean (앱 시작 시 생성, 오래 유지)
new Member(email, pw, ...)   // Entity (요청마다 생성, 금방 사라짐)
new JoinRequest(...)         // DTO (요청마다 생성, 금방 사라짐)
```

GC(Garbage Collector) = 더 이상 참조되지 않는 Heap 객체를 자동으로 메모리에서 제거.
개발자가 직접 메모리 해제를 안 해도 되는 이유가 GC 때문이다.

**Stack — 메서드 실행 시 생성, 스레드마다 독립**

```java
public Member findById(Long id) {  // id → 이 스레드의 Stack에 저장
    Member result = repo.findById(id).orElseThrow(); // result → Stack에 저장
    return result;
} // 메서드 끝나면 Stack에서 제거
```

1000명이 동시에 `findById()`를 호출해도 각자의 Stack에 각자의 `id`와 `result`가 저장된다.
같은 `MemberService` Bean(Heap)을 공유하지만 데이터는 절대 섞이지 않는다.
**싱글톤 Bean이 안전한 이유의 핵심이 여기 있다.**

---

## 16. 접근제어자 — private / protected / public / (package)

| 접근제어자 | 같은 클래스 | 같은 패키지 | 상속(extends) | 외부 |
|---|---|---|---|---|
| `private` | ✅ | ❌ | ❌ | ❌ |
| `(없음)` | ✅ | ✅ | ❌ | ❌ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| `public` | ✅ | ✅ | ✅ | ✅ |

### 네 프로젝트 예시

```java
public class Member {
    private String email;          // 외부 직접 수정 불가 — getter로만 읽기
    private boolean banned;

    protected Member() { }         // JPA 전용 통로 — 외부 new Member() 차단

    public static Member registerGeneral(...) { } // 외부 유일한 생성 입구
    public void ban() { ... }                     // 외부에서 호출 가능한 도메인 메서드

    private static String requireText(...) { }    // 클래스 내부 검증 유틸, 외부 노출 불필요
}
```

### protected + 상속 — JPA가 protected를 쓸 수 있는 이유

`implements UserDetails`, `extends JpaRepository`는 상속이 아니라 **인터페이스 구현/상속**이라 protected와 관계없다.

`protected`가 중요한 건 **클래스 상속(extends)** 맥락이다:

```java
// 부모
public class Animal {
    protected String name;
    protected void breathe() { }
}

// 자식 — protected 멤버 접근 가능
public class Dog extends Animal {
    public void bark() {
        System.out.println(name + " 짖음"); // ✅ 부모 protected 필드 접근
    }
}
```

JPA가 `protected Member() { }`를 쓸 수 있는 이유도 여기서 나온다.
JPA는 Lazy Loading을 위해 런타임에 네 Entity를 **상속한 프록시 클래스**를 내부적으로 만들기 때문:

```java
// JPA가 런타임에 만드는 것 (의사코드)
class Member$HibernateProxy extends Member {
    Member$HibernateProxy() {
        super(); // Member를 상속했으니 protected 생성자 호출 가능
    }
}
```

`private`으로 막으면 JPA 프록시가 `super()` 호출을 못 해서 오류가 난다.
`protected`가 정확히 "JPA 프록시에만 구멍을 내주는" 이유가 이거다.

---

## 17. 인터페이스 vs 상속 — 헷갈리는 두 개념

```java
// 상속 (extends) — 부모 클래스의 구현을 물려받음
public class Dog extends Animal {
    // Animal의 메서드를 그대로 쓰거나 override
}

// 인터페이스 구현 (implements) — 계약만 이행, 구현은 직접
public class CustomUserDetails implements UserDetails {
    // UserDetails가 요구하는 메서드를 직접 구현해야 함
}

// 인터페이스 상속 (extends) — 인터페이스끼리
public interface MemberRepository extends JpaRepository<Member, Long> {
    // JpaRepository의 메서드 + 추가 메서드 선언
}
```

**인터페이스 = 계약서.** "이 메서드들을 반드시 구현하겠다"는 약속.
Spring Security가 `UserDetails` 타입만 알고 있는 덕분에, `CustomUserDetails`가 들어와도 동작한다.

```java
// Spring Security 내부 — 구체 클래스를 모름
UserDetails user = userDetailsService.loadUserByUsername(email);
user.isAccountNonExpired(); // UserDetails 계약만 믿고 호출

// 실제로 들어온 건 CustomUserDetails지만 문제없음
// → 이게 DIP(의존 역전 원칙)의 실천
```

---

## 18. Spring MVC 흐름

HTTP 요청이 들어오면 이 순서로 처리된다:

```
클라이언트 요청
      ↓
  [Filter]              ← Spring 밖. 서블릿 레벨. JWT 검증이 여기
      ↓
DispatcherServlet       ← Spring MVC의 관문. 모든 요청이 여기 먼저 옴
      ↓
HandlerMapping          ← "이 URL은 어떤 Controller가 처리하지?" 찾아줌
      ↓
  [Interceptor]         ← Spring 안. Controller 전후 처리
      ↓
Controller              ← 요청 받아서 Service 호출
      ↓
Service                 ← 비즈니스 로직
      ↓
Repository              ← DB 접근
      ↓
(역순으로 응답 반환)
```

DispatcherServlet = **모든 요청의 교통정리역**. `@Controller`, `@RestController`의 URL을 보고 맞는 메서드를 찾아 실행시킨다.

---

## 19. AOP와 @Transactional의 기원 — 왜 만들어졌나

JDBC 시절 DB 트랜잭션 코드:

```java
// 옛날 방식 — 비즈니스 로직보다 인프라 코드가 더 많음
Connection conn = null;
try {
    Class.forName("com.mysql.jdbc.Driver");
    conn = DriverManager.getConnection("jdbc:mysql://...", "user", "pw");
    conn.setAutoCommit(false);  // 트랜잭션 시작

    PreparedStatement ps = conn.prepareStatement("INSERT INTO member ...");
    ps.setString(1, email);
    ps.executeUpdate();

    conn.commit();              // 커밋
} catch (SQLException e) {
    conn.rollback();            // 롤백
} finally {
    conn.close();               // 연결 반환
}
```

문제가 세 가지였다:

```
① 모든 메서드마다 이 코드가 반복됨 — 수백 줄의 복붙
② 핵심 로직(INSERT)이 인프라 코드(트랜잭션)에 묻혀버림
③ conn.close() 빠뜨리면 DB 커넥션 누수 → 서버 다운
```

**Spring이 한 것:**

"트랜잭션 코드는 공통이다. 따로 빼서 자동으로 끼워 넣자." → **AOP**

```java
// 개발자가 쓰는 코드
@Transactional  // "트랜잭션 처리는 Spring한테 맡길게"
public Member registerMember(JoinMemberRequest request) {
    Member member = Member.registerGeneral(...); // 핵심 로직만 남음
    return memberRepository.save(member);
}
```

AOP + Proxy가 이걸 가능하게 한다:

```java
// Spring이 런타임에 만드는 프록시 (의사코드)
class MemberAccountService$$Proxy extends MemberAccountService {
    @Override
    public Member registerMember(JoinMemberRequest request) {
        // 옛날에 개발자가 직접 쓰던 코드를 Spring이 대신 실행
        트랜잭션_시작();
        try {
            Member result = super.registerMember(request); // 네 코드
            트랜잭션_커밋();
            return result;
        } catch (RuntimeException e) {
            트랜잭션_롤백();
            throw e;
        }
    }
}
```

**흐름 정리:**

```
옛날: 개발자가 직접 conn → setAutoCommit → try → commit/rollback → close
Spring: @Transactional 하나로 위 모든 과정을 AOP 프록시가 자동 처리
```

---

## 20. @Transactional 핵심 정리

### 기본 동작

```java
@Transactional  // 없으면 메서드마다 트랜잭션 없이 실행
public void createOrder(...) {
    // 이 메서드 안의 모든 DB 작업이 하나의 트랜잭션
    // 중간에 RuntimeException 터지면 전부 롤백
}
```

### readOnly = true

```java
@Transactional(readOnly = true) // 조회 전용 선언
public Member findById(Long id) { ... }
```

`readOnly`와 propagation은 별개 설정이다. propagation의 업그레이드가 아님.

- `propagation` = 트랜잭션을 **어떻게 참여하느냐** (새로 만들지, 기존 것에 합류할지)
- `readOnly` = 이 트랜잭션이 **읽기 전용이냐** (최적화)

**readOnly=true가 Dirty Checking을 끄는 이유:**

Dirty Checking = JPA가 영속 엔티티를 조회할 때 원본 스냅샷을 찍어두고, commit 시점에 현재 상태와 비교해서 달라졌으면 자동으로 UPDATE 쿼리를 실행하는 기능.

```java
@Transactional  // readOnly 없음
public void someMethod() {
    Member member = memberRepository.findById(1L).get(); // 스냅샷 저장
    member.changeNickname("새닉네임"); // 변경
    // save() 호출 안 해도 commit 시 자동 UPDATE → Dirty Checking
}
```

```java
@Transactional(readOnly = true)
public Member findById(Long id) {
    // "어차피 안 바꿀 거니까 스냅샷 찍지 마"
    // Dirty Checking 비활성화 → 메모리, CPU 절약
    return memberRepository.findById(id).orElseThrow();
}
```

### propagation

```java
@Transactional(propagation = Propagation.REQUIRED)  // 기본값
// 이미 트랜잭션 있으면 참여, 없으면 새로 시작

@Transactional(propagation = Propagation.REQUIRES_NEW)
// 무조건 새 트랜잭션 시작 (기존 트랜잭션 일시 중단)
```

### self-invocation — 트랜잭션이 사라지는 함정

```java
@Service
public class OrderService {

    @Transactional
    public void createOrder() { ... }

    public void process() {
        this.createOrder(); // ❌ 트랜잭션 없음
    }
}
```

`this`는 Java에서 "현재 객체 자신"을 가리키는 키워드다. 필드명과 파라미터명이 겹칠 때 구분 용도로도 쓰지만, 근본은 **현재 인스턴스의 참조**다.

문제는 여기서 나온다:

```
외부에서 호출:  프록시.process() → 프록시 안에서 실제객체.process() 호출
                                  → 실제객체.createOrder() 호출
                                  → 트랜잭션 없음 (프록시 거치지 않음)

this = 프록시가 아닌 실제 객체를 가리킴
     → this.createOrder() = 프록시 우회 = @Transactional 무효
```

Spring의 AOP 프록시는 **외부에서 들어오는 호출만 가로챌 수 있다.** 클래스 내부의 `this.method()` 호출은 프록시를 건너뛰고 실제 객체를 직접 호출한다.

---

## 21. Filter vs Interceptor

```
요청
  ↓
[Filter]          ← Servlet 컨테이너. Spring 밖
  ↓
DispatcherServlet ← Spring 진입
  ↓
[Interceptor]     ← Spring 안. Controller 전후
  ↓
Controller
```

| | Filter | Interceptor |
|---|---|---|
| 위치 | Spring 밖 (서블릿) | Spring 안 |
| Spring Bean DI | 직접 받아야 함 | 자동 |
| 주 용도 | JWT 인증, CORS, 인코딩 | 로깅, 공통 데이터 세팅 |

**JWT를 Filter에서 처리하는 이유:**

인증 실패 시 요청 자체를 Spring에 넘기기 전에 차단해야 한다. Interceptor는 이미 DispatcherServlet을 통과한 후라 너무 늦다.

---

---

## 22. JPA 영속성 상태 4단계

JPA가 엔티티를 관리하는 방식. 영속성 컨텍스트(1차 캐시)가 핵심.

```
[비영속]  new Member()               → JPA가 전혀 모름
    ↓ repository.save() 또는 findById()
[영속]    JPA가 관리 중              → 스냅샷 찍음, 변경 감지됨
    ↓ 트랜잭션 종료 or detach()
[준영속]  관리 중이었다가 분리됨    → 변경해도 UPDATE 안 나감
    ↓ repository.delete()
[삭제]    삭제 예약됨                → 커밋 시 DELETE 실행
```

```java
// 영속 상태 진입
Member member = memberRepository.findById(1L).get(); // 영속 (스냅샷 생성)

member.changeNickname("새이름"); // 자바 객체만 수정 (DB 아직 안 바뀜)

// 트랜잭션 커밋 시점 → JPA가 스냅샷과 비교 → 달라졌으면 자동 UPDATE
// = Dirty Checking
```

---

## 23. @Transactional 속성 전체 정리

```java
@Transactional(
    readOnly    = false,                       // 기본. true면 Dirty Checking 비활성화
    rollbackFor = RuntimeException.class,      // 기본. Checked Exception은 롤백 안 함
    propagation = Propagation.REQUIRED,        // 기본. 트랜잭션 참여 방식
    isolation   = Isolation.DEFAULT            // 기본. DB 격리 수준
)
```

### 실무 사용 기준

```
DB read만 있음          → @Transactional(readOnly = true)
DB write가 하나라도     → @Transactional
외부 API + DB write     → @Transactional(rollbackFor = Exception.class)
Redis만 (DB 없음)       → 트랜잭션 불필요
```

### 클래스 기본값 패턴 (실무 표준)

```java
// 조회 위주 서비스
@Transactional(readOnly = true)      // 클래스 기본값
public class OrderService {
    public Orders getOrder() { ... } // readOnly 상속
    
    @Transactional(rollbackFor = Exception.class) // write만 override
    public Orders confirmOrder() { ... }
}

// 외부 API 연동 서비스
@Transactional(rollbackFor = Exception.class)  // 클래스 기본값
public class PaymentService {
    @Transactional(readOnly = true)  // 조회만 override
    public List<PaymentHistoryDto> getHistories() { ... }
    
    public void processPayment() { ... } // 기본값 상속
}
```

---

## 24. Checked Exception 롤백 이슈

```
@Transactional 기본 → RuntimeException(Unchecked)만 롤백
                     → Checked Exception(IOException 등)은 롤백 안 함
```

```java
// 위험한 코드 — Before
@Transactional
public void cancelPayment() throws IOException {
    paymentHistory.markCanceled(); // DB 변경
    iamportClient.cancel();        // IOException 발생!
    // → IOException은 Checked → 롤백 안 됨 → DB는 취소됐는데 Iamport는 취소 안 됨
}

// 안전한 코드 — After
@Transactional(rollbackFor = Exception.class) // 방법 1
public void cancelPayment() {
    try {
        iamportClient.cancel();
    } catch (IOException e) {
        throw new PaymentCancelFailureException(e.getMessage()); // 방법 2: RuntimeException 포장
    }
    paymentHistory.markCanceled(); // API 성공 확인 후에만 DB 변경 (순서 중요!)
}
```

---

## 25. DB Isolation (격리 수준) — 동시성 문제

| 수준 | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| READ_UNCOMMITTED | 발생 | 발생 | 발생 |
| READ_COMMITTED | 방지 | 발생 | 발생 |
| REPEATABLE_READ | 방지 | 방지 | 발생 (MySQL은 거의 방지) |
| SERIALIZABLE | 방지 | 방지 | 방지 |

**REPEATABLE_READ 핵심:** 트랜잭션 A가 시작할 때 스냅샷을 찍음. 이후 트랜잭션 B가 같은 row를 수정하고 커밋해도, A는 자신의 스냅샷 기준으로 읽음. A와 B는 서로 기다리지 않음 — 독립적으로 커밋.

**Phantom Read:** 기존 row 변경은 막히지만, 새로 INSERT된 row는 보일 수 있음. "없던 유령이 끼어드는" 현상.

실무에서는 DB 기본값(MySQL InnoDB = REPEATABLE_READ) 유지. SERIALIZABLE은 성능 문제로 거의 미사용.

---

## 26. Redis — 개념 및 자료구조

Redis를 DB 대신 쓰는 이유: 메모리 기반이라 10~100배 빠름 + TTL로 자동 만료.

**주요 자료구조:**

```
String  → 단순 키-값. JWT 블랙리스트, RefreshToken 저장 (EX TTL)
ZSet    → score로 정렬되는 집합. 랭킹(조회수), 최근 본 상품(timestamp)
Hash    → 필드별 저장. 세션 대체, 구조적 데이터
List    → 순서 있는 목록. 큐/스택 구현
Set     → 중복 없는 집합. 태그, 좋아요 집합
```

**ZSet (Sorted Set) 핵심:**
```
ZADD key score member   → 추가/수정
ZREVRANGE key 0 9       → score 높은 순 상위 10개
ZINCRBY key 1 member    → score +1 (조회수)
ZCARD key               → 원소 개수
ZREMRANGEBYRANK key 0 0 → 가장 낮은 score 제거
```

> 프로젝트 적용 세부 내용: `arrange_CART_ORDER_PAYMENT.md` 섹션 4 참고

---

## 27. @Transactional Override — 세트 정리

### @Transactional 기본값 (아무 설정 안 했을 때)

```
rollbackFor  = RuntimeException.class   ← Unchecked만 롤백
readOnly     = false
propagation  = REQUIRED
isolation    = DEFAULT (DB 기본값)
```

### 케이스별 선택 기준

| 상황 | 걸어줄 어노테이션 |
|---|---|
| DB 조회만 (쓰기 없음) | `@Transactional(readOnly = true)` |
| DB 쓰기 (일반) | `@Transactional` |
| Checked Exception이 메서드 밖으로 나올 때 | `@Transactional(rollbackFor = Exception.class)` |
| try-catch로 이미 RuntimeException 포장 | `@Transactional` 기본으로 충분 |

### 클래스 vs 메서드 override — 핵심

```java
@Transactional(rollbackFor = Exception.class)  // ← 클래스 기본값
public class PaymentService {

    @Transactional(readOnly = true)   // ← 메서드가 클래스 설정을 통째로 교체
    public List<...> getPaymentHistories(...) { ... }
    //  readOnly=true 는 적용됨
    //  rollbackFor=Exception.class 는 사라짐 → 기본값(RuntimeException)으로 돌아감
}
```

> **교체(replace), 합산(merge) 아님.** 메서드에 @Transactional 붙이는 순간 클래스 설정이 통째로 무시됨.

### Checked Exception 롤백 흐름

```
try-catch로 포장하는 경우:
  try {
      iamportClient.cancelPaymentByImpUid(...)  // Checked Exception
  } catch (IamportResponseException | IOException e) {
      throw new PaymentCancelFailureException(e.getMessage());  // RuntimeException
  }
  → 클래스 밖으로 Checked Exception 안 나감 → rollbackFor 불필요

throws로 밖으로 내보내는 경우:
  public void someMethod() throws IOException {
      ...  // Checked Exception 직접 던짐
  }
  → 반드시 @Transactional(rollbackFor = Exception.class) 필요
```

---

## 28. Bean / Thread / Stack / Heap / Singleton 심화 정리

### Bean 한 줄 정의

> **"Spring이 생성하고 관리하는 객체. 개발자가 new 안 해도 Spring 컨테이너가 대신 만들고 싱글톤으로 Heap에 올려두며 필요한 곳에 자동 주입(DI)해준다."**

### Bean 등록 방식 두 가지

```
내가 만든 클래스         → @Component / @Service / @Controller / @Repository
외부 라이브러리 객체      → @Configuration + @Bean

// 외부 라이브러리는 내가 소스 수정 불가 → 직접 @Bean으로 수동 등록
@Configuration
public class AppConfig {
    @Bean
    public IamportClient iamportClient() {
        return new IamportClient(apiKey, apiSecret);
    }
}
```

### @Component 직접 쓰는 경우

```
@Service    → 비즈니스 로직
@Controller → HTTP 처리
@Repository → DB 접근
위 셋 어디에도 안 맞는 애매한 위치 → @Component

예) JwtProvider, CustomLogoutFilter, MemberAuthorizationUtil
```

### Bean vs Entity — 자주 혼동

| | Bean | Entity |
|---|---|---|
| 예시 | CartService, PaymentService | Member, Orders, Cart |
| 관리 주체 | Spring 컨테이너 | JPA |
| 생명주기 | 앱 시작 ~ 종료 (싱글톤) | 요청마다 new, 트랜잭션 끝나면 GC |
| Heap 위치 | ✅ | ✅ (하지만 싱글톤 아님) |

→ Entity는 Bean이 아님. 둘 다 Heap에 있지만 관리 주체와 생명주기가 다름.

### Thread / Stack / Heap 구조

```
요청 A → Thread-1 → Stack-1 (memberId=1, quantity=3 ...)  ← 스레드 독립
요청 B → Thread-2 → Stack-2 (memberId=2, quantity=5 ...)  ← 스레드 독립
                          ↓ 둘 다
                     Heap (CartService Bean 1개 공유)
```

- **Stack**: 메서드 파라미터 + 지역변수. 스레드마다 독립. 메서드 끝나면 자동 소멸.
- **Heap**: new로 생성된 모든 객체. Bean(싱글톤) + Entity(요청마다 생성) 모두 여기.
- **IamportClient 같은 외부 라이브러리도** @Bean으로 등록되면 Heap에 싱글톤으로 올라감.

### Stateless가 싱글톤 안전 조건인 이유

```java
// ❌ 위험 — stateful
@Service
public class CartService {
    private List<Cart> carts;  // Heap에 고정. 모든 스레드가 같은 변수 공유
}

// ✅ 안전 — stateless
@Service
public class CartService {
    private final CartRepository cartRepository;  // Bean(처리 도구)만

    public void addCart(Long memberId, int quantity) {
        List<Cart> carts = ...;  // 지역변수 → Stack → 스레드 독립
    }
}
```

인스턴스 변수 자리 = Bean(클래스 타입)만 → stateless → 싱글톤 안전.
인스턴스 변수 자리 = 일반 데이터 → stateful → 모든 스레드 공유 → 위험.

### 정적 팩토리 메서드에서 new를 숨기는 이유

```java
// new를 직접 노출하면
Orders order = new Orders();  // 필수 필드 빠져도 컴파일 통과 → 잘못된 상태 생성 가능

// 팩토리 메서드로 숨기면
Orders order = Orders.createOrder(member, address, payMethod, ...);
// → 필수값 강제 / 의미 있는 이름 / 잘못된 상태 생성 자체를 차단
```

### Reflection

> **"실행 중(런타임)에 클래스 정보를 들여다보고 조작하는 기능."**

```java
Class<?> clazz = CartService.class;
clazz.getMethods();      // 메서드 목록 조회
clazz.getAnnotations();  // @Service 같은 어노테이션 조회
clazz.newInstance();     // 객체 생성
```

Spring이 @Service, @Component 붙은 클래스를 스캔해서 Bean으로 등록하는 것 자체가 Reflection으로 동작함. @Transactional 프록시 생성, JPA 기본 생성자 호출도 마찬가지. 개발자가 직접 쓸 일은 거의 없고 프레임워크 내부에서 사용.

### 면접 답변 — Bean

> "Spring이 생성하고 관리하는 객체입니다. 개발자가 직접 new로 생성하지 않고 Spring 컨테이너가 대신 생성해서 싱글톤으로 관리하며 필요한 곳에 자동으로 주입(DI)합니다. Bean은 반드시 stateless여야 하는데, Heap에 하나만 올라가 모든 스레드가 공유하기 때문입니다. 저는 프로젝트의 모든 Service Bean에 인스턴스 변수로 처리 도구(Bean)만 두고 요청 데이터는 파라미터와 지역변수로만 처리해 이 원칙을 지켰습니다."

---

## 29. N+1 문제

### 발생 조건

```
1. @OneToMany (연관관계)
2. FetchType.LAZY (기본값)
3. 루프 안에서 연관 데이터 접근
```

### 흐름

```java
List<Orders> orders = orderRepository.findAll();  // 쿼리 1번

for (Orders order : orders) {
    order.getOrderProducts();  // 루프마다 쿼리 N번 추가 발생
}
// 주문 10개 → 총 11번 쿼리
// 주문 100개 → 총 101번 쿼리
```

핵심 원인: Lazy = "접근할 때 조회" → 루프마다 DB 왔다갔다.

### 해결법

```java
// 1. Fetch Join — JPQL로 한 번에 JOIN
@Query("SELECT o FROM Orders o JOIN FETCH o.orderProducts WHERE o.member.id = :memberId")
List<Orders> findAllWithOrderProducts(@Param("memberId") Long memberId);
// → 쿼리 1번에 Orders + OrderProduct 모두 가져옴

// 2. EntityGraph — 어노테이션으로 선언
@EntityGraph(attributePaths = {"orderProducts"})
List<Orders> findAll();
```

| | Fetch Join | EntityGraph |
|---|---|---|
| 방식 | JPQL 직접 작성 | 어노테이션 선언 |
| 적합한 경우 | 복잡한 조건, 여러 연관관계 | 단순 조회 |
| 페이징 | 주의 필요 (메모리 처리 위험) | 동일 |
| 유연성 | 높음 | 낮음 |

### 면접 답변

> "N+1 문제는 Lazy Loading 설정된 연관관계를 루프에서 접근할 때 쿼리가 1+N번 발생하는 문제입니다. findAll()로 전체 조회 후 각 엔티티의 연관 데이터에 접근하면 건마다 SELECT가 추가로 나갑니다. Fetch Join으로 한 번의 쿼리에 연관 데이터까지 함께 가져와 해결하고, 단순 조회는 EntityGraph로 간결하게 처리합니다."

### 꼬리 질문

**Q. Eager로 바꾸면 안 되나?**
→ Eager는 항상 JOIN. 필요 없는 데이터도 항상 끌고 옴 → 더 나쁨. Fetch Join은 필요할 때만 명시적으로 가져오는 것.

**Q. Fetch Join + 페이징 같이 쓰면?**
→ 컬렉션 Fetch Join + pageable = 메모리에서 전체 로드 후 페이징 → 위험. @BatchSize 또는 쿼리 분리로 해결.

---

## 30. Index (인덱스)

### 왜 빠른가

```
인덱스 없음 → Full Table Scan → 전체 row 순차 탐색 → O(N)
인덱스 있음 → B-Tree 탐색 → O(log N)
```

책에서 목차로 페이지 바로 찾는 것과 동일.

### 자동 vs 수동 생성

```java
@Id                       // PK → 자동 인덱스
@Column(unique = true)    // unique 제약 → 자동 인덱스

@Table(indexes = {
    @Index(name = "idx_member_email", columnList = "email"),
    @Index(name = "idx_order_member", columnList = "member_id")
})
@Entity
public class Member { ... }  // 수동 지정
```

### 어디에 걸어야 하나

```
걸어야 하는 곳                이유
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
WHERE 조건으로 자주 쓰이는 컬럼  조회 성능
JOIN ON 조건 컬럼               JOIN 속도
ORDER BY 컬럼                   정렬 성능
FK (외래키)                     JOIN + 조회

걸지 말아야 하는 곳            이유
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
INSERT/UPDATE/DELETE 많은 컬럼 쓸 때마다 인덱스 갱신 → 쓰기 느려짐
카디널리티 낮은 컬럼           성별(M/F)처럼 값 종류 적으면 효과 없음
```

### 복합 인덱스

```java
@Index(columnList = "member_id, product_management_id")
// 순서 중요: member_id 단독 조회 가능. product_management_id 단독은 인덱스 안 탐.
```

### 면접 답변

> "인덱스는 B-Tree 구조로 특정 컬럼을 정렬해두어 O(log N)으로 빠르게 찾을 수 있게 합니다. WHERE 조건, JOIN, ORDER BY에 자주 쓰이는 컬럼에 걸고, 쓰기가 많은 컬럼은 인덱스 갱신 비용이 생겨 신중하게 겁니다."

**꼬리 질문 — 인덱스 많으면 좋은 거 아님?**
→ INSERT/UPDATE/DELETE 시 인덱스도 같이 갱신 → 쓰기 성능 저하. 꼭 필요한 곳에만.

**꼬리 질문 — 카디널리티?**
→ 컬럼 값의 다양성. email은 모두 달라 높음(효과 좋음). 성별은 M/F뿐이라 낮음(효과 없음).

---

## 31. Connection Pool (HikariCP)

### 왜 필요한가

```
Connection Pool 없음:
  요청마다 → DB 연결 생성(TCP 3-way handshake) → 쿼리 → 연결 끊음
  → 연결 생성 자체가 비쌈 (10~100ms)

Connection Pool 있음 (HikariCP):
  앱 시작 시 → 연결 N개 미리 생성해 풀에 보관
  요청 올 때 → 풀에서 꺼내 씀 → 반납
  → 연결 생성 비용 0
```

### 흐름

```
Thread-1 요청 → 풀에서 Connection 꺼냄 → 쿼리 → 반납
Thread-2 요청 → 풀에서 Connection 꺼냄 → 쿼리 → 반납

풀 사이즈 = 10, 동시 요청 11개?
→ 11번째 스레드는 반납될 때까지 대기 (timeout 초과 시 예외)
```

### 설정

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # 최대 연결 수 (기본값 10)
      minimum-idle: 5           # 최소 유지 연결 수
      connection-timeout: 30000 # 연결 대기 최대 시간 (ms)
```

### 면접 답변

> "Connection Pool은 DB 연결을 미리 만들어두고 재사용하는 방식입니다. 매 요청마다 연결을 새로 생성하면 TCP 핸드셰이크 비용이 발생합니다. HikariCP는 Spring Boot 기본 Pool로 풀 사이즈만큼 연결을 유지하다가 요청이 오면 꺼내주고 끝나면 반납받습니다. 스레드 수보다 Pool 크기가 작으면 대기가 발생하므로 적절한 크기 설정이 중요합니다."

---

## 32. @ControllerAdvice / @ExceptionHandler

### 왜 쓰냐

```java
// 각 Controller마다 try-catch → 중복
// @RestControllerAdvice 한 곳에서 전역 처리
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<String> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(PaymentCancelFailureException.class)
    public ResponseEntity<String> handleCancelFailure(PaymentCancelFailureException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
```

### 처리 흐름

```
Controller → Service → 예외 발생
    ↓ (전파)
DispatcherServlet
    ↓
@RestControllerAdvice → @ExceptionHandler 매핑 → 응답 반환
```

### 면접 답변

> "@ControllerAdvice는 전역 예외 처리 클래스입니다. @ExceptionHandler로 예외 타입별 공통 응답을 반환해 각 Controller마다 try-catch를 반복하지 않고 한 곳에서 관리합니다. 일관된 에러 응답 형식을 유지할 수 있습니다."

---

## 33. Filter vs Interceptor vs AOP

### 위치

```
클라이언트 요청
    ↓
[ Filter ]          ← Spring 밖 (서블릿 레벨)
    ↓
DispatcherServlet
    ↓
[ Interceptor ]     ← Spring 안, Controller 앞뒤
    ↓
Controller
    ↓
[ AOP ]             ← 메서드 실행 앞뒤 (Service까지)
    ↓
Service
```

### 각각 언제 쓰냐

```
Filter      → JWT 검증, CORS, 인코딩. Spring Context 없이도 동작. 요청 자체 차단
Interceptor → 로그인 여부, URL 접근 제한. HandlerMethod 접근 가능
AOP         → @Transactional, 로깅, 실행시간 측정. 메서드 단위 횡단 관심사
```

### 면접 답변

> "셋 다 공통 기능을 끼워넣는 방식이지만 위치가 다릅니다. Filter는 서블릿 레벨로 Spring 밖에 있어 JWT 검증처럼 요청 자체를 차단할 때 씁니다. Interceptor는 Controller 앞뒤에서 동작하고, AOP는 메서드 단위로 Service까지 포함합니다. 제 프로젝트에서 JWT 처리는 Filter, 트랜잭션은 AOP로 처리했습니다."

> 프로젝트 적용 세부 내용: `arrange_JWT.md` 참고

---

## 34. GC (Garbage Collection)

### 한 줄 정의

> "더 이상 참조되지 않는 Heap 객체를 JVM이 자동으로 메모리에서 제거하는 기능."

### Heap 구조 + GC 흐름

```
Heap
├── Young Generation
│   ├── Eden      ← new 하면 여기 먼저
│   ├── Survivor0
│   └── Survivor1
└── Old Generation ← 오래 살아남은 객체

흐름:
객체 생성 → Eden
    ↓ Eden 가득 차면
Minor GC → 살아남으면 Survivor 이동
    ↓ 반복 생존
Old Generation 승격
    ↓ Old 가득 차면
Full GC → Stop-The-World (모든 스레드 일시 정지) → 응답 지연
```

### Bean vs Entity GC 관점

```
Bean (CartService 등) → 앱 시작~종료까지 Old Generation 유지. GC 대상 아님
Entity (Member 등)    → 요청마다 new → 트랜잭션 끝나면 참조 끊김 → Minor GC 정리
```

### 면접 답변

> "GC는 JVM이 Heap에서 참조 없는 객체를 자동 제거하는 기능입니다. Young Generation에서 Minor GC가 자주 일어나고 오래 살아남으면 Old Generation으로 이동합니다. Full GC 시 Stop-The-World로 응답이 멈출 수 있어 튜닝 포인트가 됩니다. Spring Bean은 앱 종료까지 유지되어 GC 대상이 아니고, 요청마다 생성되는 Entity가 주로 GC 대상입니다."

---

## 35. SOLID 원칙

### S — 단일 책임 원칙 (SRP)

```
하나의 클래스는 하나의 책임만.
CartService → 장바구니 로직만. 이메일 발송 X, 리포트 생성 X.
```

### O — 개방/폐쇄 원칙 (OCP)

```
확장엔 열려있고, 수정엔 닫혀있어야.
새 결제 방식 추가 → 기존 코드 수정 없이 구현체만 추가.
```

### L — 리스코프 치환 원칙 (LSP)

```
부모 타입 자리에 자식 타입이 들어가도 동작해야.
List<Cart> carts = new ArrayList<>();  // OK
```

### I — 인터페이스 분리 원칙 (ISP)

```
뚱뚱한 인터페이스 하나보다 작은 인터페이스 여러 개.
안 쓰는 메서드를 억지로 구현하게 만들지 말 것.
```

### D — 의존 역전 원칙 (DIP)

```java
private final CartRepository cartRepository;  // 인터페이스에 의존
// Spring이 JpaRepository 구현체를 주입 → 구현체 몰라도 됨
```

### 면접 답변

> "SOLID 중 제 프로젝트에서 가장 명확히 적용된 건 SRP와 DIP입니다. CartService는 장바구니 로직만, PaymentService는 결제 로직만 담당해 책임을 분리했고, DIP는 Spring DI가 자동으로 적용해줘서 Service는 Repository 인터페이스에만 의존하고 구현체는 몰라도 됩니다."

---

## 36. JWT vs Session

### Session 방식 — 가상 데이터 흐름

```
[로그인]
사용자: email="hong@gmail.com", pw="1234" 전송

서버:
  1. DB에서 hong@gmail.com 조회 → 비밀번호 확인
  2. 세션 생성
     세션ID  = "abc123xyz"
     세션내용 = { memberId: 42, role: "USER", email: "hong@gmail.com" }
     서버 메모리(Map)에 저장:
       sessions["abc123xyz"] = { memberId: 42, role: "USER" }
  3. 쿠키 반환: Set-Cookie: JSESSIONID=abc123xyz

[다음 요청]
브라우저: Cookie: JSESSIONID=abc123xyz 자동 전송
서버:
  sessions["abc123xyz"] 조회 → { memberId: 42 } 꺼냄 → 처리

[로그아웃]
서버 메모리에서 sessions["abc123xyz"] 삭제
→ 이후 abc123xyz로 요청 → "세션 없음" → 401
```

### 세션 공유 문제 — 서버 여러 대일 때

```
[서버 1대 — 정상]
사용자 A → 서버1 로그인
서버1 메모리: sessions["abc123"] = { memberId: 42 }
사용자 A → 서버1 재요청 → 세션 있음 → OK

[서버 3대 + 로드밸런서 — 문제]
사용자 A → 로드밸런서 → 서버1 로그인
  서버1 메모리: sessions["abc123"] = { memberId: 42 }
  서버2 메모리: {} (없음)
  서버3 메모리: {} (없음)

사용자 A → 로드밸런서 → 서버2로 라우팅  ← 다른 서버로 보냄
  서버2: sessions["abc123"] 없음 → "로그인하세요" → 문제!

해결책:
  1. Sticky Session  → 같은 사용자 = 같은 서버. 서버 죽으면 세션도 사라짐
  2. Redis 세션 서버 → 세션을 외부 Redis에 모아서 관리. 어느 서버든 동일하게 조회
  3. JWT            → 토큰에 정보 담음. 서버 메모리 안 씀. 어느 서버든 검증 가능
```

### JWT 방식 — 실제 데이터 가공 흐름

```
[로그인]
hong@gmail.com / 1234 전송

서버:
  AccessToken 생성:
    Header  = { alg: "HS256", typ: "JWT" }
    Payload = { memberId: 42, role: "USER", exp: 1720000000 }  ← 15분 후 만료
    Signature = HMAC_SHA256(Header+Payload, "my-secret-key")

    최종 토큰 (Base64 인코딩):
    eyJhbGciOiJIUzI1NiJ9.eyJtZW1iZXJJZCI6NDJ9.xK2mP...

  RefreshToken 생성:
    Payload = { memberId: 42, exp: 1720600000 }  ← 7일 후 만료
    eyJhbGciOiJIUzI1NiJ9.eyJtZW1iZXJJZCI6NDJ9.yL3nQ...

응답:
  Authorization: Bearer eyJhbGciOi...xK2mP     ← AccessToken (헤더)
  Set-Cookie: refreshToken=eyJhbGci...; HttpOnly; Secure  ← RefreshToken (쿠키)

[다음 요청 — 주문 조회]
브라우저: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...xK2mP

JwtAuthenticationFilter:
  1. Authorization 헤더에서 토큰 추출
  2. Signature 검증 → "my-secret-key"로 위조 여부 확인
  3. Payload 디코딩 → memberId: 42, role: "USER", exp: 1720000000
  4. 만료시간 확인 → 현재시간 < exp → 유효
  5. SecurityContext 등록: Authentication = { memberId: 42, role: "USER" }
  6. Controller → Service → verifyUserIdMatch(42) → 통과

[로그아웃]
AccessToken 남은 만료시간 = 8분 = 480초
  → Redis: SET blacklist:eyJhbGciOi...xK2mP "" EX 480
  → 이후 같은 토큰 요청 시 JwtFilter가 블랙리스트 확인 → 거부
RefreshToken 쿠키: Max-Age=0 → 삭제
```

### JWT 구조 요약

```
Header.Payload.Signature

Header    → 알고리즘 (HS256)
Payload   → memberId, role, 만료시간 (Base64. 누구나 볼 수 있음. 민감정보 X)
Signature → 서버 비밀키로 서명 → 변조하면 검증 실패
```

### 대형 SPA (Kream / 29cm / Musinsa) 와 비교

| | 내 프로젝트 | 대형 SPA |
|---|---|---|
| 로그인 방식 | 이메일/비번 + 커스텀 JWT | 소셜 로그인 (카카오/네이버/애플) 중심 + OAuth2 |
| AccessToken 위치 | Authorization 헤더 | 메모리 (XSS 방어용. localStorage X) |
| RefreshToken 위치 | HttpOnly 쿠키 ✅ | HttpOnly 쿠키 ✅ 동일 |
| 토큰 갱신 | 별도 구현 | RefreshToken Rotation (갱신 시 RefreshToken도 교체) |
| 로그아웃 | Redis 블랙리스트 ✅ | Redis 블랙리스트 + 모든 기기 로그아웃 지원 |
| 인증 서버 | Spring Security 내장 | 별도 Auth 서버 (MSA 분리) |
| 회원가입 | 이메일 인증 | 소셜 계정 연동 + 전화번호 인증 |

**내 프로젝트가 실무와 다른 핵심 2가지:**

```
1. RefreshToken Rotation 없음
   대형 서비스: RefreshToken 쓸 때마다 새 RefreshToken 발급 (탈취 감지 가능)
   내 프로젝트: RefreshToken 7일 고정 (탈취되면 7일간 유효)

2. 소셜 로그인이 부가 기능
   대형 서비스: 카카오/네이버가 메인. 이메일 가입이 오히려 부가
   내 프로젝트: 이메일이 메인, 소셜이 부가 → 실제 SPA 사용자 패턴과 반대
```

### 면접 답변

> "Session은 서버 메모리에 상태를 저장해 서버 확장 시 세션 공유 문제가 생깁니다. 로드밸런서로 다른 서버로 라우팅되면 세션이 없어 재로그인이 필요합니다. JWT는 토큰 자체에 정보를 담아 Stateless하게 처리하므로 어느 서버든 검증 가능합니다. 제 프로젝트는 AccessToken(15분)과 RefreshToken(7일)을 분리하고, 로그아웃 시 Redis 블랙리스트에 등록해 즉시 무효화했습니다."

**꼬리 질문 — JWT Payload 암호화 안 되면 위험하지 않음?**
→ Payload는 Base64 인코딩이라 누구나 볼 수 있음. 비밀번호 같은 민감 정보는 절대 넣으면 안 됨. 위조 방지는 Signature가 담당. 변조하면 Signature 검증 실패.

**꼬리 질문 — RefreshToken 탈취되면?**
→ 내 프로젝트는 7일간 유효. 대형 서비스는 Rotation으로 탈취 즉시 감지 (이미 쓴 토큰으로 재요청 오면 이상 감지 → 전체 로그아웃).


> 프로젝트 적용 (KakaoOAuthClient WebClient 리팩토링): `arrange_OAuth2.md` 참고
> Bearer 토큰 추출 처리: `arrange_JWT.md` 참고

---

# ═══════════════════════════════════
# PART 5 — 실무 심화
# ═══════════════════════════════════

## 37. JPA 기본 생성자가 필요한 이유

JPA는 DB에서 값을 가져올 때 네가 만든 이런 생성자를 쓰기 어렵다:

```java
// 쓰기 어려운 이유: 파라미터 순서, 타입, 개수가 DB row와 안 맞을 수 있음
public Member(String email, String password, String nickname) { ... }
```

그래서 JPA는 **먼저 빈 객체를 만들고 → 필드에 값을 주입**한다:

```java
Member member = new Member();  // 기본 생성자 필요
// Reflection으로 필드에 직접 주입
member.id = 42;
member.email = "hong@gmail.com";
member.password = "...";
member.role = USER;
member.createdAt = ...;
// 연관관계 객체, 프록시도 내부적으로 주입
```

`private` 기본 생성자로 막으면 JPA(Hibernate)가 접근 불가 → `protected`로 설정:

```java
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA용. 외부 new는 차단
public class Member {
    // 팩토리 메서드로만 생성 강제
    public static Member createMember(String email, ...) { return new Member(...); }
}
```

---

## 38. 스냅샷 (Snapshot)

영속성 컨텍스트가 엔티티를 관리할 때 **최초 상태의 복사본**을 따로 보관함.

```
member = em.find(Member.class, 42);   ← DB에서 조회 → 영속 상태
  ┌─────────────────┐
  │ 영속성 컨텍스트  │
  │  member (현재)  │  ← 개발자가 직접 수정 가능
  │  snapshot (복사)│  ← JPA가 최초 상태 저장 (건드리지 않음)
  └─────────────────┘

member.setEmail("new@gmail.com");  ← 현재 객체 수정

commit() 시점:
  JPA: member(현재) vs snapshot(최초) 비교
  → email 다름 → UPDATE member SET email=? WHERE id=42 자동 실행
  → 이게 Dirty Checking
```

snapshot이 없으면 Dirty Checking 불가. 그래서 영속 상태에서만 Dirty Checking 작동.

---

## 39. 객체 그래프 (Object Graph)

연관된 엔티티들이 참조로 연결된 구조.

```
Member ─── Cart (1:N)
  │
  └─── Orders (1:N) ─── OrderProduct (1:N) ─── Product
                  └─── PaymentHistory (1:N)
```

```java
// 객체 그래프 탐색
member.getCarts();                         // Cart 목록
member.getOrders().get(0).getOrderProducts();  // 주문 → 상품까지
```

JPA는 객체 그래프를 탐색하는 순간 쿼리를 날림 (Lazy). 트랜잭션 밖에서 탐색하면 `LazyInitializationException`.

---

## 40. 단방향 vs 양방향 연관관계

```java
// 단방향 — Cart만 Member를 알고 있음
@Entity
public class Cart {
    @ManyToOne
    @JoinColumn(name = "member_id")
    private Member member;  // Cart → Member 참조 가능
}
// Member는 Cart를 모름. member.getCarts() 불가.

// 양방향 — 서로 알고 있음
@Entity
public class Member {
    @OneToMany(mappedBy = "member")  // 연관관계 주인은 Cart
    private List<Cart> carts;        // Member → Cart 참조도 가능
}
```

**연관관계 주인 (mappedBy):**
```
FK(외래키)를 가진 쪽이 주인. Cart.member_id가 FK → Cart가 주인.
mappedBy = "member" → "나(Member)는 주인 아님. Cart.member 필드가 주인."
DB 반영은 주인 쪽에서만. Member.carts에 추가해도 DB에 안 들어감.
```

**실무 관점:**
```
단방향으로 설계 시작 → 필요할 때만 양방향 추가 (순환참조 위험)
양방향은 toString(), JSON 직렬화 시 무한루프 주의
  → @JsonIgnore, @ToString.Exclude 필요
```

---

## 41. cascade / orphanRemoval / 생명주기 소유

```java
@OneToMany(
    mappedBy = "order",
    cascade = CascadeType.ALL,   // 부모 작업이 자식에 전파
    orphanRemoval = true          // 부모와 연결 끊긴 자식 자동 삭제
)
private List<OrderProduct> orderProducts;
```

**cascade 종류:**
```
PERSIST  → order 저장 시 orderProduct도 자동 저장
REMOVE   → order 삭제 시 orderProduct도 자동 삭제
ALL      → 위 전부
MERGE    → order merge 시 orderProduct도 merge
```

**orphanRemoval:**
```java
order.getOrderProducts().remove(0);  // 리스트에서 제거
// orphanRemoval=true → commit 시 DELETE order_product WHERE id=?
// orphanRemoval=false → 리스트에서만 제거. DB는 그대로.
```

**생명주기를 소유한다:**
```
cascade=ALL + orphanRemoval=true
→ 부모가 자식의 생명주기를 완전히 관리
→ 자식을 직접 Repository로 저장/삭제할 필요 없음
→ 부모 통해서만 생성/삭제

주의: 자식이 여러 부모에 공유되는 경우 사용 금지
  (OrderProduct → 하나의 Order에만 속함 → OK)
  (Product → 여러 OrderProduct에 속할 수 있음 → cascade 금지)
```

---

## 42. 파생쿼리 vs JPQL

```java
// 파생쿼리 (Spring Data JPA 메서드명 기반)
List<Cart> findByMemberId(Long memberId);
// → Spring이 메서드명 파싱 → SELECT c FROM Cart c WHERE c.member.id = ?

// JPQL (@Query 직접 작성)
@Query("SELECT o FROM Orders o JOIN FETCH o.orderProducts WHERE o.member.id = :memberId")
List<Orders> findAllWithOrderProducts(@Param("memberId") Long memberId);
```

**내 프로젝트 적용 기준:**

```
1. 기본 CRUD / 단순 단일 조건 조회
   → 파생쿼리 (findByEmail, findByMemberId 등)

2. 목록 조회 / 정렬 / 필터 / 검색
   → JPQL (@Query)

3. 연관 로딩 최적화 (N+1 방지)
   → @EntityGraph + JPQL (Fetch Join)

4. 삭제/수정 벌크 작업
   → @Modifying + JPQL
   → flush/clear 옵션 포함 (영속성 컨텍스트 동기화)

@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("DELETE FROM Cart c WHERE c.member.id = :memberId")
void deleteAllByMemberId(@Param("memberId") Long memberId);
```

---

## 43. deleteAll vs deleteAllByIdInBatch

```java
// deleteAll() — N+1 패턴
cartRepository.deleteAll(carts);
// → SELECT 전부 → DELETE 하나씩 N번
// 대상: 10개 → 쿼리 11번 (SELECT 1 + DELETE 10)

// deleteAllByIdInBatch() — 단일 벌크 쿼리
cartRepository.deleteAllByIdInBatch(cartIds);
// → DELETE FROM cart WHERE id IN (1, 2, 3, 4, ...)
// 대상: 10개 → 쿼리 1번

// 주의: deleteAllByIdInBatch는 cascade/orphanRemoval 무시
// → DB에 직접 DELETE 날림 → JPA 라이프사이클 이벤트 발생 안 함
// → 연관 데이터 정리가 필요하면 직접 해야 함
```

---

## 44. CDN / 핫링크

**CDN (Content Delivery Network):**
```
정적 파일(이미지, JS, CSS)을 전 세계 분산 서버에 캐싱
→ 사용자와 가장 가까운 서버에서 제공 → 응답 빠름

내 프로젝트: cdnjs.cloudflare.com에서 Chart.js, Three.js 로드
  <script src="https://cdnjs.cloudflare.com/ajax/libs/..."></script>
  → 내 서버에 파일 없음. Cloudflare CDN에서 바로 전달.
```

**핫링크 (Hotlinking):**
```
다른 서버의 리소스 URL을 직접 img src로 참조하는 것
<img src="https://otherdomain.com/image.jpg">  ← 핫링크

문제: otherdomain.com 서버가 트래픽 부담 → 차단 가능
     → Referer 차단 설정 시 이미지 안 뜸

S3 같은 이미지 서버를 직접 소유하거나
CDN을 통해 캐싱하면 해결
```

---

## 45. 직렬화 / 역직렬화

암기법: **직렬화 = 포장 (Java → JSON), 역직렬화 = 개봉 (JSON → Java)**

```
직렬화 (Serialization):
  Member { id:42, email:"hong@gmail.com" }
          ↓  Jackson ObjectMapper
  { "id": 42, "email": "hong@gmail.com" }
  → HTTP 응답 / Redis 저장 / 파일 저장

역직렬화 (Deserialization):
  { "access_token": "eyJhbGci...", "token_type": "bearer" }
          ↓  Jackson ObjectMapper
  OAuthTokenResponse { accessToken: "eyJhbGci...", tokenType: "bearer" }
  → .bodyToMono(OAuthTokenResponse.class) 가 역직렬화 지시
```

**개발에서 나오는 곳:**
```
@ResponseBody  → 객체 직렬화 → JSON 응답
@RequestBody   → JSON 역직렬화 → 파라미터 객체
Redis 저장/조회 → 직렬화/역직렬화
WebClient      → .bodyToMono(DTO.class) = 역직렬화
JWT Payload    → memberId, role 직렬화해서 토큰에 삽입
```

**주의사항:**
```java
// 역직렬화 시 기본 생성자 필요 (Jackson이 new DTO() 후 필드 주입)
@NoArgsConstructor
public class OAuthTokenResponse {
    @JsonProperty("access_token")   // JSON 키명이 다를 때 매핑
    private String accessToken;
}
```

---

## 46. RestTemplate → WebClient 전환 (KakaoOAuthClient)

**HTTP 클라이언트 진화:**
```
HttpURLConnection  →  RestTemplate  →  WebClient
(Java 내장, raw)      (Spring 래퍼)    (비동기, 공식 권장)
직접 스트림 열고         동기 Blocking    Non-Blocking
닫고 파싱 다 직접        Spring 6 deprecated
```

**deprecated:**
```
Spring 6.0 공식 선언: "RestTemplate is in maintenance mode"
= 버그 수정만. 새 기능 추가 없음. 나중에 삭제될 수 있음.
면접에서 "왜 RestTemplate 썼어요?" 역질문 나올 수 있음.
```

**.onStatus() — 에러 처리 선언적 분리:**
```java
// RestTemplate: 에러 처리 없음 → 4xx/5xx 와도 String으로 받아 직접 체크
ResponseEntity<String> res = restTemplate.exchange(..., String.class);
if (res.getStatusCode().is4xxClientError()) { ... }  // 직접 체크

// WebClient: .onStatus()로 선언적 처리
.onStatus(HttpStatusCode::is4xxClientError, response ->
    response.bodyToMono(String.class)
        .map(body -> new IllegalStateException("4xx: " + body))
)
// 4xx 오면 자동으로 IllegalStateException으로 변환
```

**.block() — MVC에서 비동기 결과 동기로 전환:**
```java
.bodyToMono(OAuthTokenResponse.class)  // Mono<OAuthTokenResponse> (비어있는 박스)
.block()                                // 값 올 때까지 기다려서 꺼냄 → 동기 처리
// 나중에 WebFlux 전환 시 .block() 제거 + Mono<> 반환으로만 바꾸면 됨
```

**Callback:**
```
동기(RestTemplate): Thread → 요청 → 기다림(대기) → 응답 → 처리
비동기(WebClient):  Thread → 요청 → "응답 오면 이 함수(callback) 실행해줘" 등록 → Thread 반납
                   카카오 응답 옴 → 등록된 callback 자동 실행
.bodyToMono(OAuthTokenResponse.class) 자체가 callback 등록
```

**MultiValueMap / HttpEntity / ResponseEntity:**
```java
MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
params.add("grant_type", "authorization_code");
params.add("scope", "profile");
params.add("scope", "email");  // 같은 키에 여러 값 가능 (일반 Map은 불가)

HttpEntity<MultiValueMap<...>> request = new HttpEntity<>(params, headers);
// 헤더 + 바디를 하나로 묶은 래퍼

ResponseEntity<String> response = restTemplate.exchange(...);
response.getStatusCode();   // 200, 401
response.getHeaders();      // 응답 헤더
response.getBody();         // 응답 바디
// HttpEntity + 상태코드 추가된 것
```

---

## 47. JPA save() 필요 / 불필요 기준

```
영속(Managed) 상태 → save() 불필요, dirty checking 자동
비영속(New/Transient) 상태 → save() 필요, 영속성 컨텍스트에 없으므로 등록해야 함
```

| 상황 | 상태 | save() 필요 여부 |
|---|---|---|
| `findBy*`로 조회한 엔티티 필드 변경 | 영속 | ❌ dirty checking 자동 반영 |
| `new Entity()`로 만든 객체 저장 | 비영속 | ✅ save() 호출 필요 |
| `@Transactional` 메서드 안에서 조회 후 변경 | 영속 | ❌ 트랜잭션 끝에 자동 flush |
| 준영속(detach) 후 변경 | 준영속 | ✅ merge() 또는 save() 필요 |

```java
// ❌ save() 불필요 — 영속 상태
@Transactional
public void updateEmail(Long id, String email) {
    Member member = memberRepository.findById(id).orElseThrow();
    member.changeEmail(email);  // dirty checking → commit 시 UPDATE 자동
    // memberRepository.save(member);  ← 불필요
}

// ✅ save() 필요 — 비영속 상태
@Transactional
public Member create(String email) {
    Member member = Member.create(email);  // new 객체
    return memberRepository.save(member);  // 영속성 컨텍스트에 등록 필요
}
```

**flush 시점:**
```
1. 트랜잭션 commit 직전 (자동)
2. JPQL 실행 직전 (자동, DB 동기화 필요)
3. entityManager.flush() (수동)
```

---

## 48. Proxy 내부 동작 + LazyInitializationException

**Hibernate 프록시 의사코드:**
```java
class MemberProxy extends Member {  // 진짜 Member를 상속한 가짜 객체
    private boolean initialized = false;
    private Member target = null;

    @Override
    public String getEmail() {
        if (!initialized) {
            // DB에서 SELECT Member WHERE id = ?
            target = em.find(Member.class, id);
            initialized = true;
        }
        return target.getEmail();  // 실제 객체에 위임
    }
}
```

```java
Member member = em.getReference(Member.class, 1L);  // 프록시 반환 (DB 조회 없음)
// member.class → MemberProxy (가짜)

member.getEmail();  // 여기서 처음 DB 조회 (initialized → true)
```

**LazyInitializationException 발생 조건:**
```
트랜잭션(= 영속성 컨텍스트) 종료 후 Lazy 컬렉션/연관 접근
→ DB 세션이 닫힌 상태에서 프록시 초기화 시도 → 예외

예: Service에서 member 조회 → 트랜잭션 종료 → Controller에서 member.getOrders() 접근 → 💥
```

**해결법:**
```
1. Fetch Join / EntityGraph → 한 번에 로딩
2. @Transactional 범위 확장 → Controller까지
3. DTO 변환을 트랜잭션 안에서 완료 → 컬렉션 접근 없이 반환 (권장)
```

```java
Hibernate.isInitialized(member.getOrders())  // 프록시 초기화 여부 확인
```

---

## 49. N+1 vs Row 폭발 — 두 문제 명확 구분

| 문제 | 원인 | 증상 |
|---|---|---|
| **N+1 문제** | Lazy 컬렉션 루프 접근 | 쿼리 1 + N번 실행 |
| **Row 폭발** | 1:N fetch join 시 페이징 | 데이터 중복 row, LIMIT 무의미 |

**N+1 문제:**
```java
List<Order> orders = orderRepository.findAll();  // 쿼리 1번
for (Order o : orders) {
    o.getOrderProducts().size();  // 각 Order마다 SELECT → 쿼리 N번
}
// Order 100개 → 쿼리 101번
```

**Row 폭발 (페이징 불가):**
```sql
-- Order 1개에 OrderProduct 5개인 경우 fetch join
SELECT o.*, op.*
FROM orders o
JOIN order_product op ON o.id = op.order_id
-- → Order 1행이 아니라 5행으로 뻥튀기
-- → LIMIT 10 해도 실제 Order는 2개밖에 못 가져올 수 있음
```

```
→ 컬렉션(1:N) fetch join + 페이징은 함께 쓰면 안 됨
→ HibernateJpaDialect 경고: "HHH90003004: firstResult/maxResults specified with collection fetch"
→ 해결: @BatchSize + 페이징, 또는 DTO Projection
```

---

## 50. N+1 해결 전략 비교

| 전략 | 사용 시점 | 주의사항 |
|---|---|---|
| **Fetch Join** (JPQL) | 복잡한 조건 + 단건/소량 | 컬렉션 2개 이상 동시 불가, 페이징 시 Row 폭발 |
| **@EntityGraph** | 단순 조회, 메서드 한 개 | 내부적으로 Fetch Join, 동일 제약 |
| **@BatchSize** | 컬렉션 다수 + 페이징 | N+1 대신 IN 쿼리로 일괄 처리, 완전 해결은 아님 |
| **DTO Projection** | 집계/통계/대량 조회 | 엔티티 안 씀 → LazyInit 없음, 가장 안전 |

```java
// Fetch Join (JPQL)
@Query("SELECT o FROM Orders o JOIN FETCH o.orderProducts WHERE o.member.id = :id")
List<Orders> findWithProducts(@Param("id") Long memberId);

// EntityGraph
@EntityGraph(attributePaths = {"orderProducts"})
List<Orders> findByMemberId(Long memberId);

// BatchSize — 글로벌 설정
@BatchSize(size = 100)
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
private List<OrderProduct> orderProducts;
// → Order 100개 조회 후 orderProducts → IN (id1, id2, ..., id100) 1번

// DTO Projection
@Query("SELECT new com.example.dto.OrderSummary(o.id, o.totalPrice) FROM Orders o WHERE ...")
List<OrderSummary> findOrderSummaries(...);
```

---

## 51. 엔티티 설계 — attachTo / detach 연관관계 패턴

```java
// ❌ 잘못된 방식 — Setter로 양방향 주입
cart.setMember(member);       // FK 쪽 설정
member.getCarts().add(cart);  // 반대쪽도 수동으로 설정

// ✅ 정적 팩토리 + 편의 메서드 패턴
public class Cart {
    public static Cart attachTo(Member member) {
        Cart cart = new Cart();
        cart.member = member;          // FK 설정
        member.getCarts().add(cart);   // 양방향 일관성 보장
        return cart;
    }

    public void detach() {
        this.member.getCarts().remove(this);  // 반대쪽 제거
        this.member = null;                   // FK 해제
    }
}
```

**왜 정적 팩토리?**
```
new Cart() + setter로 하면 양방향 일관성 깜빡하기 쉬움
attachTo() 하나로 호출하면 양방향이 항상 함께 설정됨
detach() 하나로 호출하면 양방향이 항상 함께 해제됨
```

---

## 52. @Builder JPA 함정 + @PrePersist

**@Builder + 필드 기본값 무시:**
```java
@Builder
@Entity
public class Member {
    private boolean isActive = true;  // 기본값 true 의도
    // @Builder는 이 기본값을 무시함
    // → Member.builder().build() → isActive = false (primitive 기본값)
}

// 해결 1: @Builder.Default
@Builder.Default
private boolean isActive = true;  // 이제 builder에서도 true로 시작

// 해결 2: @PrePersist
@PrePersist
protected void prePersist() {
    if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    if (!this.isInitialized) this.isActive = true;
}
```

**@Builder + @ManyToOne 프록시 충돌:**
```java
// JPA 프록시는 상속을 사용 → @Builder가 붙으면 전용 생성자만 노출됨
// Hibernate 프록시가 기본 생성자를 호출하지 못하는 상황 발생 가능
// → JPA 엔티티에서 @Builder는 주의해서 사용
// → @AllArgsConstructor + @NoArgsConstructor(access = PROTECTED) 조합이 안전함

// 체크리스트
// 1. @Builder 사용 시 @NoArgsConstructor(access = PROTECTED) 반드시 추가
// 2. Lazy 연관 필드 기본값은 @Builder.Default 또는 @PrePersist로 보장
// 3. 엔티티는 정적 팩토리 메서드(create/of/from) 권장 → Builder는 DTO에서 사용
```

---

## 53. httpBasic 비활성화 이유 + CSRF

**httpBasic 비활성화:**
```java
http.httpBasic(AbstractHttpConfigurer::disable);

// 이유: httpBasic = "Authorization: Basic base64(user:pass)" 헤더 방식
//       → JWT 기반 인증과 충돌 (두 방식 동시 활성화 불필요)
//       → 브라우저에서 팝업 로그인 창 뜨는 원인 (미비활성화 시)
//       → REST API에서는 명시적으로 비활성화하는 게 표준
```

**CSRF 비활성화 기준:**
```
CSRF 공격 원리:
  → 브라우저가 쿠키를 자동 전송하는 점을 악용
  → 악성 사이트에서 사용자 모르게 요청 전송 → 서버는 합법 요청으로 착각

JWT + Authorization 헤더 방식:
  → 브라우저가 Authorization 헤더를 자동 전송 안 함
  → CSRF 공격 성립 불가 → csrf().disable() 가능

JWT + HttpOnly 쿠키 방식:
  → 브라우저가 쿠키 자동 전송 → CSRF 공격 위험 존재
  → SameSite=Strict/Lax로 방어 (크로스 사이트 쿠키 전송 차단)
  → 또는 CSRF 토큰 유지

Form Login + 세션 방식:
  → 쿠키(세션ID) 자동 전송 → CSRF 필수 유지
```

---

## 54. 웹 보안 개념 정리

| 개념 | 설명 | 설정 위치 |
|---|---|---|
| **HttpOnly** | JS에서 쿠키 접근 차단 → XSS로 토큰 탈취 방지 | Set-Cookie 헤더 |
| **Secure** | HTTPS에서만 쿠키 전송 | Set-Cookie 헤더 |
| **SameSite=Strict** | 타 사이트 요청 시 쿠키 전혀 안 보냄 | Set-Cookie 헤더 |
| **SameSite=Lax** | 타 사이트의 GET (최상위 이동)만 허용 | Set-Cookie 헤더 |
| **SameSite=None** | 크로스 사이트 전송 허용 (Secure=true 필수) | 운영 환경 OAuth2 |
| **XSS** | 악성 스크립트 삽입 공격 → HttpOnly로 토큰 보호 | 입력값 검증, CSP |
| **CSRF** | 쿠키 자동전송 악용 → SameSite 또는 CSRF 토큰으로 방어 | 세션/쿠키 방식에서 필요 |
| **CORS** | 브라우저 정책 — 다른 Origin API 호출 차단 | Spring CorsConfig |

```java
// Spring Boot CORS 설정
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:3000")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowCredentials(true);  // 쿠키 포함 요청 허용
        }
    };
}
```

---

## 55. 토큰 저장 방식 비교

| 방식 | 장점 | 단점 | 추천 여부 |
|---|---|---|---|
| **localStorage** | 구현 간단, JS 접근 쉬움 | XSS 취약 — JS로 탈취 가능 | ❌ |
| **sessionStorage** | 탭 닫으면 삭제 | XSS 취약 동일 | ❌ |
| **HttpOnly 쿠키** | JS 접근 불가 → XSS 방어 | CSRF 위험 → SameSite로 방어 | ✅ 권장 |
| **메모리 (변수)** | XSS 방어 최강 | 페이지 새로고침 시 소멸 | 상황에 따라 |

```
내 프로젝트 선택: JWT + HttpOnly 쿠키
→ Access Token: HttpOnly 쿠키 (SameSite=Lax/None)
→ Refresh Token: HttpOnly 쿠키 (DB 저장 + Redis TTL)
→ CSRF 방어: SameSite 설정으로 대부분 방어
```

---

## 56. SPA vs SSR 비교

| 항목 | SPA (React/Vue) | SSR (Next.js/Thymeleaf) |
|---|---|---|
| **렌더링** | 브라우저에서 JS로 렌더링 | 서버에서 HTML 완성 후 전송 |
| **초기 로딩** | 느림 (JS 번들 다운로드 후 렌더) | 빠름 (HTML 바로 표시) |
| **SEO** | 불리 (초기 HTML 비어있음) | 유리 (크롤러가 내용 파악 가능) |
| **인증** | JWT + localStorage 또는 쿠키 | 세션 쿠키 또는 서버사이드 토큰 |
| **API 통신** | 모든 데이터를 REST API로 | 서버에서 직접 DB 조회 가능 |

```
내 프로젝트: SPA(Thymeleaf + JS 혼용) + JWT 쿠키 인증
→ 서버가 HTML 조각 제공 (SSR에 가까움)
→ 동적 기능(장바구니, 결제)은 JS + REST API
```

---

## 57. JAR 구조 + java -jar 실행 흐름

**JAR 내부 구조:**
```
myapp.jar
├── BOOT-INF/
│   ├── classes/          ← 내가 짠 코드 (.class 파일)
│   │   └── com/example/...
│   └── lib/              ← 의존성 라이브러리 (.jar들)
│       ├── spring-boot-*.jar
│       └── hibernate-*.jar
├── META-INF/
│   └── MANIFEST.MF       ← Main-Class: JarLauncher
└── org/springframework/boot/loader/
    └── JarLauncher.class ← Spring Boot 런처
```

**java -jar 실행 5단계:**
```
1. JVM이 META-INF/MANIFEST.MF 읽음
2. Main-Class: JarLauncher 확인
3. JarLauncher 실행 → BOOT-INF/lib/*.jar 클래스패스 추가
4. Start-Class 확인 → 내 @SpringBootApplication 클래스 찾음
5. SpringApplication.run() 실행 → 내장 Tomcat 기동
```

**클래스패스 (classpath):**
```
JVM이 .class 파일을 찾는 기준 경로 목록
java -cp /path/to/classes:/path/to/lib Main
= "이 경로들에서 클래스를 찾아라"
```

---

## 58. 외부 디렉토리 필요 이유

```
JAR 내부는 읽기 전용 (ZIP 아카이브)
→ JAR 안에 파일 업로드 불가
→ 업로드된 파일은 JAR 밖 OS 디렉토리에 저장해야 함

실무 패턴:
/home/ubuntu/uploads/              ← 서버 OS 로컬 디렉토리
또는 AWS S3 (권장)                 ← 서버 재시작해도 파일 보존

Spring 설정:
file.upload.path=/home/ubuntu/uploads/

@Value("${file.upload.path}")
private String uploadPath;

// 저장
File dest = new File(uploadPath + UUID.randomUUID() + ".jpg");
file.transferTo(dest);

// 정적 파일 서빙 (WebConfig)
registry.addResourceHandler("/Images/**")
        .addResourceLocations("file:" + uploadPath);
```

---

## 59. Docker — expose vs ports 차이

```yaml
# docker-compose.yml
services:
  app:
    image: myapp
    expose:
      - "8080"      # 컨테이너 내부 네트워크에만 노출
                    # 호스트 OS → 컨테이너 직접 접근 불가
                    # 다른 컨테이너끼리만 통신 가능

  nginx:
    image: nginx
    ports:
      - "80:80"     # 호스트:컨테이너 포트 매핑
                    # 외부 → 호스트 80포트 → nginx 컨테이너 80포트
```

**실무 패턴:**
```
외부 인터넷 → nginx(:80/443) → app(:8080)
                         ↑ expose로만 연결
                         ↑ app은 직접 외부 노출 안 함

이유: nginx가 SSL 종료, 로드밸런싱 담당
     app은 nginx 뒤에 숨겨서 직접 접근 차단
```

---

## 60. Nginx 리버스 프록시

```
리버스 프록시:
  클라이언트 → nginx → 백엔드 서버
  (클라이언트는 nginx만 알고, 백엔드 서버는 모름)

포워드 프록시:
  클라이언트 → 프록시 → 인터넷
  (서버는 클라이언트 IP 모름)
```

```nginx
# nginx.conf 기본 구조
server {
    listen 80;
    server_name example.com;

    location / {
        proxy_pass http://app:8080;           # Spring Boot 컨테이너로 전달
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;  # 실제 클라이언트 IP 전달
    }

    # SSL 리다이렉트
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    ssl_certificate /etc/nginx/certs/cert.pem;
    ssl_certificate_key /etc/nginx/certs/key.pem;
    ...
}
```

**nginx 역할 요약:**
```
1. SSL 종료 (HTTPS → HTTP 변환 후 내부 전달)
2. 포트 라우팅 (80/443 → 8080)
3. 정적 파일 직접 서빙 (JS/CSS/Images — Spring 대신)
4. 로드밸런싱 (다수 인스턴스로 분산)
```

---

## 61. 예외 처리 심화 — checked vs unchecked + @Transactional 롤백

**checked vs unchecked:**
| 항목 | checked 예외 | unchecked 예외(RuntimeException) |
|---|---|---|
| 컴파일 강제 | `throws`/`try-catch` 필수 | 선택적 |
| 실무 사용 | 거의 사용 안 함 | 커스텀 예외 제작 시 주로 사용 |
| 예시 | `IOException`, `SQLException` | `NullPointerException`, `IllegalStateException` |

```java
// 커스텀 예외 표준 패턴
public class MemberNotFoundException extends RuntimeException {
    public MemberNotFoundException(Long id) {
        super("Member not found: " + id);
    }
}

// 전역 예외 처리기
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<String> handle(MemberNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
```

**@Transactional 롤백 기준:**
```
기본: RuntimeException, Error → 자동 롤백
     checked 예외 → 롤백 안 함 (커밋됨)

명시 설정:
@Transactional(rollbackFor = Exception.class)           // checked도 롤백
@Transactional(noRollbackFor = IllegalArgumentException.class)  // 이건 롤백 제외
```

**throw vs throws:**
```java
// throws — 선언: "이 메서드에서 이 예외가 나올 수 있음"
public void save() throws IOException { ... }

// throw — 실행: 실제로 예외 객체를 던짐
throw new MemberNotFoundException(id);  // new 필수
```

---

## 62. Spring Data JPA — 메서드명 쿼리

```java
// 메서드명 = 쿼리 조건
Optional<Member> findByEmail(String email);
// → SELECT m FROM Member m WHERE m.email = :email

List<Order> findByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, OrderStatus status);
// → WHERE member_id = ? AND status = ? ORDER BY created_at DESC

// Boolean 필드 조건
Optional<Addresses> findByMemberIdAndDefaultAddressIsTrue(Long memberId);
// → WHERE member_id = ? AND default_address = true

// 상위 N개
List<Product> findTop5ByActiveTrueOrderByCreatedAtDesc();
```

**중요 함정:**
```
파생쿼리/JPQL에서 필드명은 DB 컬럼명이 아니라 엔티티 필드명
→ 엔티티 PK가 productId인데 id로 쓰면 PropertyReferenceException
→ @Query 에서도 SELECT p FROM Product p (테이블명 아님, 클래스명)
```

---

## 63. boolean 필드 getter 네이밍 함정

| 필드 선언 | Lombok 생성 Getter | 문제 |
|---|---|---|
| `private boolean soldOut` | `isSoldOut()` | 정상 |
| `private boolean isSoldOut` | `isSoldOut()` (중복 is) | Jackson이 `soldOut`으로 직렬화 |
| `private Boolean soldOut` | `getSoldOut()` | null 위험 |

```java
// ❌ 잘못된 패턴
private boolean isDiscount;  // Lombok → isDiscount() → Jackson → "discount" 키로 직렬화 혼란

// ✅ 올바른 패턴
private boolean discount;    // Lombok → isDiscount() → Jackson → "discount" 키로 직렬화 일관

// Boolean(래퍼) — null 체크 필수
private Boolean discount;    // null 가능 → isDiscount() 호출 시 NPE
if (product.isDiscount()) { ... }  // Boolean null이면 NPE!
// → Boolean.TRUE.equals(product.getDiscount()) 안전한 null-safe 방식
```

**도메인 명확화:**
```
null: "할인 여부 미정" — 의미 있는 null이면 Boolean
false: "할인 아님" — 확실한 값이면 boolean + 기본값

설계 원칙: boolean 필드는 primitive(boolean)로 선언, 기본값 지정으로 null 원천 차단
```

---

---

## 핵심 요약

| 개념 | 한 줄 |
|---|---|
| Class / Instance / Bean | 설계도 / new로 만든 객체 / Spring이 관리하는 객체 |
| IoC | 객체 제어권이 개발자 → Spring으로 역전 |
| DI | Spring이 필요한 객체를 생성자에 넣어줌 |
| ApplicationContext | Bean을 담는 컨테이너 |
| 싱글톤 | Bean은 앱에 하나, 모든 요청이 공유 — 무상태여야 안전 |
| 생성자 주입 | final 보장 / 순환참조 조기 발견 / 테스트 용이 |
| 객체 복원 | DB의 흩어진 row 값을 Java 객체 하나로 재조립 |
| 기본 생성자 | JPA 복원용 통로. PROTECTED로 외부 차단 |
| DTO vs Entity | 임시 포장지 vs 핵심 도메인 객체. 섞으면 계층 침범 |
| @Transactional 프록시 | 네 코드에 없음. Spring이 감싼 프록시에 있음 |
| static final | 클래스 로딩 시점 초기화 → Spring DI 개입 불가 |
| Lazy Loading | 접근 시점에 DB 조회. 트랜잭션 밖에서 접근하면 예외 |
| 팩토리 메서드 | new를 숨겨서 잘못된 객체 생성 자체를 차단 |
| JVM Stack | 지역변수/파라미터 저장. 스레드마다 독립 → 싱글톤이 안전한 이유 |
| JVM Heap | new 객체 저장. 앱 전체 공유. GC가 정리 |
| JVM Method Area | 클래스 정보, static 저장. 앱 시작 시 고정 |
| protected 생성자 | JPA 프록시(자식 클래스)는 접근 가능, 외부 new는 차단 |
| 인터페이스 | 구현 계약. Spring이 구체 클래스 몰라도 동작하는 이유 |
| implements vs extends | 인터페이스 구현(계약 이행) vs 클래스 상속(구현 물려받음) |
| Spring MVC 흐름 | Filter → DispatcherServlet → Interceptor → Controller → Service → Repository |
| DispatcherServlet | 모든 요청의 관문. URL 보고 맞는 Controller 찾아 위임 |
| AOP | 공통 기능(트랜잭션, 로깅)을 비즈니스 로직에서 분리해 자동으로 끼워 넣음 |
| Proxy (AOP) | Spring이 네 클래스를 상속한 프록시 객체를 Bean으로 등록. @Transactional이 여기 있음 |
| @Transactional | 트랜잭션 시작/커밋/롤백을 AOP 프록시가 자동 처리 |
| readOnly=true | Dirty Checking 비활성화 → 조회 성능 최적화. 쓰기 실수 시 예외 발생 |
| Dirty Checking | 영속 엔티티 변경을 JPA가 자동 감지 → commit 시 UPDATE 자동 실행 |
| 영속성 상태 4단계 | 비영속(new) → 영속(관리중) → 준영속(분리) → 삭제 |
| Checked Exception 롤백 | 기본 @Transactional은 Checked 롤백 안 함 → rollbackFor or RuntimeException 포장 |
| propagation | 트랜잭션 참여 방식. REQUIRED(기존 참여), REQUIRES_NEW(새로 시작) |
| self-invocation | 같은 클래스 내부 this.method() 호출은 프록시 우회 → @Transactional 무효 |
| Isolation | 동시 트랜잭션 간 데이터 보호 수준. REPEATABLE_READ = 스냅샷 기반 읽기 |
| Phantom Read | REPEATABLE_READ에서 새로 INSERT된 row가 끼어드는 현상 |
| Redis ZSet | score로 정렬되는 자료구조. 랭킹(조회수), 최근 본 상품(timestamp) 모두 ZSet |
| @Transactional override | 메서드 어노테이션이 클래스 어노테이션을 통째로 교체. 합산 아님. rollbackFor도 사라짐 |
| Filter vs Interceptor | Filter=Spring 밖(서블릿), Interceptor=Spring 안. JWT는 Filter에서 처리 |
| Bean 정의 | Spring이 생성하고 관리하는 객체. 싱글톤으로 Heap에 1개. DI로 자동 주입 |
| Bean vs Entity | Bean=Spring 관리 싱글톤 / Entity=JPA 관리, 요청마다 new, GC 대상 |
| @Component | Controller/Service/Repository 어디도 아닌 애매한 위치에 사용 |
| @Configuration+@Bean | 외부 라이브러리 객체를 Spring Bean으로 수동 등록할 때 |
| Thread | 요청 1개 = 스레드 1개. 각자 독립된 Stack 보유 |
| Stack | 파라미터/지역변수 저장. 스레드 독립. 메서드 끝나면 소멸 |
| Heap | 모든 객체(Bean+Entity) 저장. 앱 전체 공유. GC 관리 |
| Stateless 필요 이유 | Bean은 Heap에 1개 → 인스턴스 변수 모든 스레드 공유 → 데이터 섞임 방지 |
| Reflection | 런타임에 클래스 정보 조회/조작. Spring의 Bean 등록, 프록시 생성 내부 동작 원리 |
| N+1 문제 | Lazy + 루프 접근 → 쿼리 1+N번 발생. Fetch Join 또는 EntityGraph로 해결 |
| Fetch Join vs EntityGraph | 복잡한 조건/페이징 → Fetch Join. 단순 조회 → EntityGraph. 용도 차이 |
| Index | B-Tree로 O(log N) 탐색. WHERE/JOIN/ORDER BY 컬럼에 걸기. 쓰기 많은 컬럼은 지양 |
| Connection Pool (HikariCP) | DB 연결 미리 생성해 재사용. 연결 생성 비용 제거. 풀 초과 시 대기 발생 |
| @ControllerAdvice | 전역 예외 처리. @ExceptionHandler로 예외 타입별 공통 응답 반환. 중복 제거 |
| Filter vs Interceptor vs AOP | Filter=서블릿(JWT), Interceptor=Controller 앞뒤, AOP=메서드 단위(@Transactional) |
| GC | Heap의 참조 없는 객체 자동 제거. Full GC 시 Stop-The-World 발생. Bean은 GC 대상 아님 |
| SOLID | SRP(단일책임), OCP(확장개방), LSP(치환), ISP(인터페이스분리), DIP(의존역전) |
| JWT vs Session | Session=서버 상태저장(stateful). JWT=토큰에 정보담아 stateless. 서버 확장에 유리 |
| RestTemplate vs WebClient | RestTemplate=동기Blocking, Spring6 deprecated. WebClient=비동기, .onStatus() 에러처리, Bean 재사용 |
| WebClient .block() | MVC 환경에서 동기처럼 사용. 추후 비동기 전환 시 .block()만 제거 |
| Bearer 제거 | header.substring("Bearer ".length()) → 순수 토큰값 추출 |
| save() 필요 기준 | 비영속(new) → save() 필요. 영속(findBy* 조회) → dirty checking 자동 |
| Proxy 내부 | 상속 기반 가짜 객체. initialized 플래그로 DB 조회 지연. 트랜잭션 밖 접근 → LazyInitializationException |
| N+1 vs Row 폭발 | N+1 = Lazy 루프 → 쿼리 N번. Row 폭발 = 1:N fetch join + 페이징 → 데이터 중복 |
| N+1 해결 | Fetch Join(복잡조건) / EntityGraph(단순) / @BatchSize(페이징) / DTO Projection(대량) |
| attachTo/detach | 정적 팩토리로 양방향 연관관계 일관성 보장. Setter 직접 사용 금지 |
| @Builder JPA 함정 | @Builder는 필드 기본값 무시 → @Builder.Default 또는 @PrePersist로 해결 |
| httpBasic 비활성화 | JWT 방식과 충돌 + 브라우저 팝업 방지 → AbstractHttpConfigurer::disable |
| CSRF | 쿠키 자동전송 악용. JWT+헤더는 불필요. 쿠키 방식은 SameSite로 방어 |
| HttpOnly | JS 쿠키 접근 차단 → XSS 토큰 탈취 방지 |
| SPA vs SSR | SPA=브라우저 렌더링(SEO 불리). SSR=서버 HTML 제공(SEO 유리, 초기 빠름) |
| JAR 구조 | BOOT-INF/classes(내 코드) + BOOT-INF/lib(의존성). JarLauncher → SpringApplication 순서 |
| 외부 디렉토리 | JAR 내부 읽기 전용 → 업로드 파일은 OS 디렉토리 또는 S3에 저장 |
| expose vs ports | expose=컨테이너 내부 통신. ports=호스트↔컨테이너 포트 매핑(외부 노출) |
| Nginx 리버스 프록시 | 클라이언트→nginx→Spring Boot. SSL 종료, 포트 라우팅, 정적 파일 서빙 |
| checked vs unchecked | checked=컴파일 강제(IOException). unchecked=RuntimeException(커스텀 예외 표준) |
| @Transactional 롤백 | 기본 RuntimeException+Error만 롤백. checked는 rollbackFor=Exception.class 필요 |
| 메서드명 쿼리 | findBy/And/Or/OrderBy/Top — 필드명은 엔티티 기준(DB 컬럼명 아님) |
| boolean getter 함정 | `boolean discount` → isDiscount(). `boolean isDiscount` → 중복. Boolean → null NPE 주의 |
| String = bytes | 문자열은 인코딩(UTF-8)된 byte 배열. Redis가 bytes 저장 = String도 저장 가능한 이유 |
| 인코딩 | 문자 → bytes 변환 규칙. UTF-8=범용, Base64=binary→문자열, JWT payload=Base64Url (암호화 아님) |
| 비밀번호 찾기 = 재설정 | 단방향 해시라 원본 복원 불가. 일회성 토큰 발급 → 새 비밀번호 설정 |
| 단방향 vs 양방향 | 비밀번호=BCrypt(비교만 필요). 주민번호/계좌=AES(복호화해서 사용해야 함) |
| Self-invocation | this.method() = 프록시 우회 → @Transactional 무효. 별도 빈으로 분리가 근본 해결 |
| Proxy 위치 | Spring Container가 실제 Bean 대신 Proxy를 보관. DI 받는 모든 참조 = Proxy |
| this in Service | this = 현재 실행 중인 실제 객체 (Proxy 아님). 생성자 전용 개념이 아님 |
| Checked→Unchecked 변환 | catch(IOException e) → throw new RuntimeException(). 클래스 밖으로 Checked 내보내지 않는 게 목표 |
| @Transactional 미선언 | 클래스에 선언된 경우만 상속. 클래스도 없으면 트랜잭션 없음. 자동 부여 아님 |
| @BatchSize | Lazy + IN 쿼리 배치. Pageable+FetchJoin 위험 시 대안. default_batch_fetch_size: 100 |
| JPQL :param | @Param("ids") List<Long>과 매핑. IN (1,2,...) 자동 전개. GeneratedValue 무관 |
| RefreshToken Rotation | 재발급마다 Refresh도 교체 + 기존 즉시 무효화. 탈취 토큰 재사용 시 이상 감지 |
| MVC vs WebFlux | MVC=Thread-per-request 동기 블로킹. WebFlux=이벤트 루프 비동기. WebClient는 MVC에서도 사용 가능 |
| Mono/Flux | Mono<T>=단건 비동기. Flux<T>=다건 스트림. .block()으로 동기 전환 |
| Collection 선택 | 중복/순서→ArrayList, 삽입삭제→LinkedList, 중복제거→HashSet, 같은키여러값→MultiValueMap |
| HttpEntity | RestTemplate 요청의 헤더+바디 래퍼 |
| ResponseEntity | Controller 응답의 헤더+바디+상태코드 래퍼 |
| onStatus() | WebClient 에러 처리 체인. res.bodyToMono().map(예외생성) → .block() 시점에 throw |
| ErrorCode Enum | status+code+message 통합. 예외 추가 시 핸들러 추가 불필요. 응답 포맷 일관성 |
| BusinessException | ErrorCode를 품은 RuntimeException 베이스. GlobalExceptionHandler 하나로 통합 처리 |
| OCP = DDD 같은 결 | 둘 다 "직접 건드리지 말고 계약/메서드로 소통" — 캡슐화+단일책임이 공통 뿌리 |

---

## 64. 문자열(String)은 이미 bytes다 — 인코딩 개념

**"Redis는 bytes를 저장하는데 String을 저장해도 되는 이유"**

```
컴퓨터는 문자를 모른다. 숫자(bytes)만 안다.
"A" → 65 (ASCII)
"가" → 0xEA 0xB0 0x80 (UTF-8, 3바이트)
"hello" → [104, 101, 108, 108, 111] (byte 배열)
```

String은 이미 bytes로 표현된 데이터다. **인코딩(Encoding)**이 "문자 → bytes 변환 규칙"이다.

```java
String s = "hello";
byte[] bytes = s.getBytes(StandardCharsets.UTF_8); // [104, 101, 108, 108, 111]
String back = new String(bytes, StandardCharsets.UTF_8); // "hello"
```

**Redis에서 String 저장이 가능한 이유:**
```
"logout" → StringRedisSerializer → UTF-8 bytes → Redis 저장
조회 시  → Redis → bytes → StringRedisSerializer → "logout"
```

**Java 객체(Object)와의 차이:**
```
String → UTF-8 인코딩 → bytes  (단순)
Object → Jackson JSON → bytes  (복잡, @class 타입 정보 포함)

// Redis에서 Object 저장 시 실제 저장값
{"@class":"JOO.jooshop.dto.ProfileDto","memberId":1,"imageUrl":"https://..."}
```

**인코딩 포인트:**
```
UTF-8   → 한글 포함 범용 (현재 웹 표준)
Base64  → bytes → 문자열로 표현 (이메일/JWT에서 binary 데이터 전송 시)
JWT     → Header.Payload.Signature 각 파트를 Base64Url로 인코딩 (암호화 아님)
```

---

## 65. 단방향 해시 — 비밀번호 찾기가 아니라 "재설정"이다

**단방향(Hash)이라 복원 불가 → "찾기"는 불가능, "재설정"이 정확한 표현**

```
[비밀번호 찾기 흐름]
1. 이메일 입력
2. 서버: 일회성 토큰 생성 → DB 임시 저장 (TTL: 10분) → 이메일로 재설정 링크 발송
3. 사용자: 링크 클릭 → 서버 토큰 유효성 검증
4. 새 비밀번호 입력 → BCrypt 해시 → DB 업데이트 (원본은 서버도 여전히 모름)
```

**단방향 vs 양방향 — 저장 대상 기준**

| 데이터 | 방식 | 이유 |
|---|---|---|
| 비밀번호, PIN | BCrypt (단방향) | 서버도 원문 알 필요 없음. 비교만 하면 됨 |
| 주민번호, 계좌번호 | AES (양방향) | 업무상 복호화해서 실제로 사용해야 함 |

**내 프로젝트 적용:**
```java
// 가입 시 — 단방향 해시 저장
String hashedPw = passwordEncoder.encode("1234"); // BCrypt → "$2a$10$abc..."

// 로그인 시 — 해시 비교 (원문 복원 없이)
passwordEncoder.matches("1234", storedHash); // true/false
```

---

# ═══════════════════════════════════
# PART 7 — 심화 실전
# ═══════════════════════════════════

## 66. Self-Invocation — 프록시가 우회되어 트랜잭션이 사라지는 이유

### Proxy와 @Transactional — 왜 Proxy일 때만 적용되나?

```
@Transactional은 네 코드 안에 없다.
Spring이 Proxy 클래스에 트랜잭션 코드를 심어놓은 것이다.

// Spring이 내부적으로 만드는 Proxy (개념 코드)
class MemberAccountServiceProxy extends MemberAccountService {

    @Override
    public Member registerMember(JoinMemberRequest request) {
        // @Transactional 코드가 여기에 있음 (네 코드 아님)
        TransactionStatus tx = transactionManager.begin();
        try {
            Member result = super.registerMember(request); // 실제 네 코드
            transactionManager.commit(tx);
            return result;
        } catch (RuntimeException e) {
            transactionManager.rollback(tx);
            throw e;
        }
    }
}
```

**this가 Service에서 나오는 이유:**
```java
// this = "지금 실행 중인 나 자신의 인스턴스 참조" — 생성자 전용이 아님
// 생성자에서 자주 보이는 이유: 파라미터 이름과 필드 이름 구분용일 뿐

// 생성자에서의 this
public Member(String email) {
    this.email = email; // this.email = 필드, email = 파라미터
}

// 서비스 메서드에서의 this
public class MemberAccountService {
    public void doSomething() {
        this.findMemberById(1L); // this = 현재 실행 중인 실제 객체 (Proxy 아님!)
    }
}
```

**핵심 메커니즘:**
```
[Spring Container]
  "memberAccountService" → 저장된 것 = MemberAccountServiceProxy

[Controller DI 주입]
  private final MemberAccountService memberAccountService;
  → 주입받은 것 = Proxy

[Controller에서 호출] — 정상
  memberAccountService.registerMember(req)
  = Proxy.registerMember(req) → @Transactional 적용 ✅

[Service 내부에서 this 호출] — 트랜잭션 소멸
  this.registerMember(req)
  = 실제객체.registerMember(req) → Proxy 우회 → @Transactional 없음 ❌
```

**내 프로젝트 위험 시나리오:**
```java
// MemberAccountService: 클래스 @Transactional(readOnly=true)
// ❌ 이런 편의 메서드를 추가하면 즉시 버그
public void registerAndVerify(JoinMemberRequest req) {  // @Transactional 없음
    registerMember(req);   // this.registerMember() → @Transactional 무시됨
    verifyEmail(savedId);  // this.verifyEmail() → @Transactional 무시됨
    // 두 메서드 모두 readOnly=true 트랜잭션에서 실행 → DB 쓰기 실패!
}

// ✅ 현재 구조 — Controller가 각 메서드를 직접 호출
// Controller → Proxy.registerMember() → @Transactional 적용 ✅
// Controller → Proxy.verifyEmail()    → @Transactional 적용 ✅
```

**해결 방법:**
```
1. 별도 클래스/빈으로 분리 (근본 해결)
2. AopContext.currentProxy() — 설정 필요, 코드 지저분
3. 구조적 예방 — 같은 클래스 내부에서 @Transactional 메서드 호출 지양
```

---

## 67. Checked vs Unchecked Exception — 실무 기준

**기준:**
```
Checked   = 외부 자원 접근 실패 (파일, 네트워크, 외부 API)
            → IOException, SQLException, IamportResponseException
            → 컴파일러가 처리 강제

Unchecked = 비즈니스 규칙 위반 or 개발자 실수
            → 대부분의 도메인 예외
            → 컴파일러 강제 없음
```

**"호출자도 처리를 강제받는다" — Checked를 그대로 두면:**
```java
// ❌ Checked를 변환 없이 선언하면
public void cancelPayment() throws IOException { ... }

// 호출자(Controller)가 강제로 처리해야 함
@PostMapping("/cancel")
public ResponseEntity<?> cancel() {
    paymentService.cancelPayment(); // 컴파일 에러! IOException 처리 안 했음
}
// → Controller도 throws 선언 → GlobalExceptionHandler 동작 안 함 → 계층 전체 오염
```

**내 프로젝트 — Checked → Unchecked 변환 (PaymentService):**
```java
try {
    cancelResponse = iamportClient.cancelPaymentByImpUid(cancelData);
} catch (IamportResponseException | IOException e) {
    log.error("Iamport 환불 API 실패: {}", e.getMessage());
    throw new PaymentCancelFailureException(e.getMessage()); // RuntimeException으로 변환
}
// 이후 클래스 밖으로는 Checked Exception 0개 → GlobalExceptionHandler 일관 처리
```

**"Runtime이 예상 불가"의 진짜 의미:**
```
NullPointerException은 어디서나 null이 들어올 수 있음
→ 모든 메서드마다 try-catch 강제하면 코드 불가능
→ 컴파일러 강제 없이 GlobalExceptionHandler에서 중앙 처리가 효율적

PaymentCancelFailureException — 명시적으로 처리하고 싶지만
RuntimeException으로 만든 이유: 컴파일러 강제 불필요 + GlobalExceptionHandler 처리 충분
```

**실무 기준 (내 프로젝트도 이 방향 ✅):**
```
모든 비즈니스 예외 → RuntimeException 상속 커스텀 예외
외부 Checked → try-catch → RuntimeException으로 변환
GlobalExceptionHandler → 예외 타입별 HTTP 상태 코드 매핑
```

---

## 68. @BatchSize + Pageable — 페이징과 연관 조회 전략

**JOIN FETCH + Pageable이 위험한 이유:**
```
Order 1 → OrderProduct 3개 → 3행
Order 2 → OrderProduct 4개 → 4행  (JOIN 결과 총 7행)

LIMIT 5 → 7행 중 5행만 → Order 2 데이터 불완전!
Hibernate 경고: 메모리에서 전체 페이징 → OOM 위험
```

**해결 — @BatchSize:**
```java
// 1단계: Orders만 페이징 (안전)
Page<Orders> orders = orderRepository.findByMemberId(memberId, pageable);

// 2단계: OrderProduct → Lazy + @BatchSize
@BatchSize(size = 100)
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
private List<OrderProduct> orderProducts;
// → SELECT * FROM order_product WHERE order_id IN (1,2,...,20) → 쿼리 1번
```

**전역 설정 (권장):**
```yaml
spring.jpa.properties.hibernate.default_batch_fetch_size: 100
```

**JPQL :param 바인딩:**
```java
@Query("SELECT o FROM Orders o JOIN FETCH o.orderProducts WHERE o.id IN :ids")
List<Orders> findWithProductsByIds(@Param("ids") List<Long> ids);
// :ids = 호출자가 넘긴 List<Long> 값들로 치환
// IN (1, 2, 3, ...) 으로 Hibernate가 자동 전개
// GeneratedValue(AUTO_INCREMENT)와 완전히 다른 개념
```

**N+1 해결 비교:**
```
N+1 (페이징 없음)  → Fetch Join / EntityGraph (쿼리 1번)
N+1 (페이징 있음)  → @BatchSize (2번 쿼리, 페이징 안전)
BatchSize limit 설정: 페이지당 20건이면 size=100~500 (페이지 크기보다 크게)
```

---

## 69. RefreshToken Rotation

```
현재 내 프로젝트:
  재발급 → Access Token만 새로 발급
  Refresh Token = 7일 고정 → 탈취 시 7일간 유효, 감지 불가

Rotation 적용:
  재발급 → Access + Refresh 둘 다 새로 발급
  기존 Refresh 즉시 무효화
  탈취된 토큰으로 재요청 → 이미 삭제된 토큰 → 이상 감지 → 전체 로그아웃
```

```java
public TokenPair reissue(String refreshToken) {
    jwtUtil.validateToken(refreshToken);

    // 없으면 이미 사용된 토큰 (탈취 의심)
    RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
        .orElseThrow(() -> new IllegalStateException("이미 사용된 토큰 — 탈취 의심"));

    refreshTokenRepository.delete(stored);  // 기존 즉시 무효화

    Long memberId = jwtUtil.getMemberId(refreshToken);
    String newAccess  = jwtUtil.createAccessToken(memberId, role);
    String newRefresh = jwtUtil.createRefreshToken(memberId, role);

    refreshTokenRepository.save(new RefreshToken(memberId, newRefresh));
    return new TokenPair(newAccess, newRefresh);
}
```

---

## 70. MVC vs WebFlux

```
Spring MVC:
  Thread-per-request. I/O 대기 중 Thread 블로킹.
  Tomcat 기본 200 Thread → 동시 200 요청 한계.
  적합: 일반 CRUD, DB 위주

Spring WebFlux:
  이벤트 루프 (Thread 2~4개). I/O 대기 → Thread 해방.
  적합: WebSocket, 스트리밍, MSA 대량 동시접속
  단점: JPA 호환 안 됨(R2DBC 필요), 러닝커브

내 프로젝트: MVC + WebClient
  → 외부 API 호출만 WebClient. .block()으로 동기처럼 사용.
  → WebFlux 전환 시 .block() 제거만 하면 됨.
```

**코드 비교:**
```java
// MVC Controller
@GetMapping("/orders/{id}")
public ResponseEntity<OrderDto> getOrder(@PathVariable Long id) {
    return ResponseEntity.ok(orderService.findById(id)); // blocking
}

// WebFlux Controller
@GetMapping("/orders/{id}")
public Mono<ResponseEntity<OrderDto>> getOrder(@PathVariable Long id) {
    return orderService.findById(id)         // Mono<OrderDto> — non-blocking
        .map(dto -> ResponseEntity.ok(dto));
}

// Mono<T>  = 0~1개 비동기 컨테이너 ("결과가 오면 이걸로 처리해")
// Flux<T>  = 0~N개 비동기 스트림
// .block() = 지금 당장 실행하고 기다려 (동기 전환)
```

---

## 71. Collection 종류 + 업그레이드

```
List (순서 있음, 중복 허용)
  ├── ArrayList   — 배열 기반, 조회 O(1), 삽입/삭제 O(N)
  └── LinkedList  — 연결 기반, 조회 O(N), 삽입/삭제 O(1)

Set (순서 없음, 중복 불허)
  ├── HashSet        — 순서 없음
  └── LinkedHashSet  — 삽입 순서 유지

Map (key-value, Collection 아님)
  ├── HashMap         — 순서 없음
  ├── LinkedHashMap   — 삽입 순서 유지
  └── TreeMap         — key 정렬

업그레이드:
  MultiValueMap     ← Map의 한계(key당 값 1개) 극복 → Map<K, List<V>>
                      HTTP 헤더, Form 파라미터 (같은 key에 여러 값)
  ConcurrentHashMap ← Thread-unsafe 극복 → 멀티스레드 안전
  ArrayDeque        ← Stack + Queue 모두 가능

선택 기준:
  중복/순서      → ArrayList
  삽입/삭제 빈번 → LinkedList
  중복 제거      → HashSet
  같은 key 여러값→ MultiValueMap
  스레드 공유    → ConcurrentHashMap
```

---

## 72. HttpEntity / ResponseEntity / onStatus

```java
// HttpEntity — 헤더+바디 묶음 (RestTemplate 요청 시)
HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
restTemplate.exchange(url, POST, request, String.class);

// ResponseEntity — HttpEntity + HTTP 상태코드 (Controller 응답)
return ResponseEntity.status(201).body(new MemberResponseDto(member));
return ResponseEntity.ok(dto);            // 200
return ResponseEntity.notFound().build(); // 404

// onStatus — WebClient 에러 처리 체인
.onStatus(HttpStatusCode::is4xxClientError, res ->
    res.bodyToMono(String.class)                      // 에러 바디 읽기 (Mono<String>)
       .map(body -> new IllegalStateException(body))  // String → 예외 객체 변환 (Mono<Throwable>)
)
// onStatus가 Mono<Throwable>을 받음 → .block() 시점에 해당 예외 throw

// WebClient / RestTemplate = HTTP 클라이언트
// 내 서버 → Iamport API / 카카오 서버 / 네이버 서버 (요청 + 응답 처리)
// RestTemplate → 동기. Spring 6 deprecated 예정.
// WebClient    → 비동기. 현재 공식 권장.
```

---

## 73. OCP = DDD — 같은 결

```
OCP (Open-Closed Principle):
  확장에는 열려 있고, 수정에는 닫혀 있다.
  새 기능 추가 시 기존 코드 수정 없이 구현체만 추가.

  ❌ if ("iamport") ... else if ("kakao") ... (추가마다 수정)
  ✅ PaymentGateway 인터페이스 → 구현체만 추가

DDD 도메인 메서드:
  ❌ order.setStatus("CANCEL") — 어디서든 상태 변경 가능, 규칙 강제 불가
  ✅ order.cancel()           — 내부에서 규칙 검증 후 변경

같은 결인 이유:
  둘 다 "캡슐화 + 단일 책임"에서 나옴
  공통: "직접 건드리지 말고 계약(인터페이스/메서드)으로 소통"
```

---

## 74. 프로젝트 설계 구조 — 실무 기준 비교

### @Transactional 선언 규칙

```java
// 클래스에 선언 → 메서드는 상속 (자동이 아닌 "상속")
@Transactional(readOnly = true)
public class OrderService {
    public List<Orders> getOrders() { ... }  // readOnly=true 상속
    @Transactional  // override → readOnly=false
    public void confirmOrder() { ... }
}

// 클래스에 없음 → 메서드에도 없음 = 트랜잭션 없음. Spring 자동 부여 X
// @ModelAttribute와 다름: @Transactional은 선언한 곳에만 동작
```

### Exception 설계 — 현재 vs 실무 개선

**현재 프로젝트:**
```java
// ResponseMessageConstants — 문자열 상수만 (상태코드/도메인코드 없음)
public static final String MEMBER_NOT_FOUND = "회원을 찾을 수 없습니다.";

// 예외마다 핸들러 하나씩 (현재 약 15개)
@ExceptionHandler(MemberNotFoundException.class)
public ResponseEntity<ErrorResponse> handle(MemberNotFoundException ex) {
    return buildResponse(HttpStatus.NOT_FOUND, ResponseMessageConstants.MEMBER_NOT_FOUND);
}

// JWTFilterV3 → {"error": "UNAUTHORIZED", "message": "..."}  ← 포맷 다름
// SecurityConfig → {"message": "Unauthorized"}               ← 포맷 또 다름
// GlobalExceptionHandler → {status, error, message, timestamp} ← 세 곳이 다름!
```

**실무 개선 — ErrorCode Enum + BusinessException:**
```java
// 1. ErrorCode.java (신규)
public enum ErrorCode {
    MEMBER_NOT_FOUND(404, "M001", "회원을 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(404, "P001", "상품을 찾을 수 없습니다."),
    PAYMENT_CANCEL_FAILURE(400, "P002", "결제 취소 실패하였습니다."),
    TOKEN_INVALID(401, "T001", "유효하지 않거나 만료된 토큰입니다."),
    TOKEN_BLACKLISTED(403, "T002", "로그아웃 처리된 토큰입니다."),
    UNAUTHORIZED(401, "A002", "인증이 필요합니다."),
    ACCESS_DENIED(403, "A003", "접근 권한이 없습니다.");

    private final int status;
    private final String code;    // 프론트와 약속한 도메인 에러 코드
    private final String message;
    // constructor + getters
}

// 2. BusinessException.java (신규 — 베이스 예외)
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}

// 3. MemberNotFoundException.java (변경)
public class MemberNotFoundException extends BusinessException {
    public MemberNotFoundException() { super(ErrorCode.MEMBER_NOT_FOUND); }
}

// 4. ErrorResponse.java (code 필드 추가)
public class ErrorResponse {
    private int status;
    private String code;      // "M001" 신규
    private String error;
    private String message;
    private LocalDateTime timestamp;

    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
            errorCode.getStatus(), errorCode.getCode(),
            HttpStatus.valueOf(errorCode.getStatus()).getReasonPhrase(),
            errorCode.getMessage()
        );
    }
}

// 5. GlobalExceptionHandler — 15개 → 1개로 통합
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
    ErrorCode code = ex.getErrorCode();
    return ResponseEntity.status(code.getStatus()).body(ErrorResponse.from(code));
}

// 6. JWTFilterV3.writeErrorResponse — ErrorCode 사용으로 포맷 통일
private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    if (response.isCommitted()) return;
    response.setStatus(errorCode.getStatus());
    response.setContentType("application/json;charset=UTF-8");
    objectMapper.writeValue(response.getWriter(), ErrorResponse.from(errorCode));
}
// 사용: writeErrorResponse(response, ErrorCode.TOKEN_INVALID);

// 7. SecurityConfig.exceptionHandling — 포맷 통일
.exceptionHandling(ex -> ex
    .authenticationEntryPoint((req, res, e) -> {
        res.setStatus(ErrorCode.UNAUTHORIZED.getStatus());
        res.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(res.getWriter(), ErrorResponse.from(ErrorCode.UNAUTHORIZED));
    })
    .accessDeniedHandler((req, res, e) -> {
        res.setStatus(ErrorCode.ACCESS_DENIED.getStatus());
        res.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(res.getWriter(), ErrorResponse.from(ErrorCode.ACCESS_DENIED));
    })
)

// 최종 응답 — 세 곳 모두 동일한 포맷:
// {"status":401,"code":"T001","error":"Unauthorized","message":"유효하지 않거나 만료된 토큰입니다.","timestamp":"..."}
```

**변경 범위:**
```
신규: ErrorCode.java, BusinessException.java
수정: ErrorResponse.java, 커스텀 예외 클래스들, GlobalExceptionHandler, JWTFilterV3, SecurityConfig
삭제: ResponseMessageConstants.java
```

### Entity 설계 — 현재 수준 평가

```java
// ✅ 현재 프로젝트 (실무 수준)
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {
    public static Member registerGeneral(...) { return new Member(...); } // 팩토리 메서드
    public void activate() { this.active = true; }                        // 도메인 메서드
}

// 보완: 컬렉션 필드 초기화
@OneToMany(mappedBy = "member")
private List<Cart> carts = new ArrayList<>();  // ✅ NPE 방지
```

### Validation 설계

```java
// 현재 ✅
@NotBlank @Email private String email;
// Controller: public ResponseEntity<?> join(@Valid @RequestBody JoinMemberRequest request)

// 실무 추가 — 크로스 필드 검증 (비밀번호 일치)
// 현재: Service에서 수동 검증
// 개선: @PasswordMatch 커스텀 어노테이션 → DTO 레벨 검증
```

### Security 평가

```
✅ 이중 FilterChain (/api/** STATELESS + /** Form Login)
✅ JWT HttpOnly 쿠키
✅ Redis Blacklist 로그아웃
✅ JWTFilterV3에 writeErrorResponse 이미 구현

개선 포인트:
  [ ] ErrorCode 기반 응답 포맷 통일 (위 섹션)
  [ ] RefreshToken Rotation
```

---

## 75. Checked / Unchecked — 실무 시나리오

```java
// ===== Checked가 강제되는 케이스 =====
// 외부 라이브러리가 강제 (Iamport, Java IO)
iamportClient.cancelPaymentByImpUid(cancelData);  // throws IOException, IamportResponseException

// ===== 현재 프로젝트 처리 방식 (실무 표준) =====
try {
    cancelResponse = iamportClient.cancelPaymentByImpUid(cancelData);
} catch (IamportResponseException | IOException e) {
    throw new PaymentCancelFailureException(e.getMessage()); // Unchecked로 변환
}
// 이후 Controller/GlobalExceptionHandler까지 Checked 0개

// ===== 모든 도메인 예외 → RuntimeException =====
throw new MemberNotFoundException("hong@gmail.com");    // 404
throw new InvalidCredentialsException("비밀번호 불일치"); // 400
throw new PaymentCancelFailureException("Iamport 오류"); // 400

// Checked를 직접 쓰는 경우 (실무에서 드묾):
// SDK/라이브러리 제작 시 "사용자가 반드시 처리해야 한다"는 API 계약 표현
```

---

## 76. WebFlux 상세 — MVC 코드 비교

**Thread 모델:**
```
MVC (Tomcat):     요청 1 = Thread 1. I/O 대기 중 블로킹. 기본 200 Thread 한계.
WebFlux (Netty):  이벤트 루프 Thread 2~4개. I/O 대기 → Thread 해방 → 수천 동시 처리.
```

**Service 비교:**
```java
// MVC
public OrderDto findById(Long id) {
    return orderRepository.findById(id).orElseThrow(...); // blocking
}

// WebFlux (R2DBC 필요)
public Mono<OrderDto> findById(Long id) {
    return orderRepository.findById(id)
        .switchIfEmpty(Mono.error(new OrderNotFoundException(...)))
        .map(OrderDto::from);
}
```

**외부 API 호출:**
```java
// MVC + WebClient — .block()으로 동기처럼 (내 프로젝트 방식)
return webClient.post().uri("/token")
    .bodyValue(params).retrieve()
    .bodyToMono(OAuthTokenResponse.class)
    .block();  // Thread 대기. MVC에서 어차피 블로킹.

// WebFlux — .block() 없이
public Mono<OAuthTokenResponse> getToken(String code) {
    return webClient.post().uri("/token")
        .bodyValue(params).retrieve()
        .bodyToMono(OAuthTokenResponse.class);
    // 결과 올 때까지 Thread 해방 → 응답 오면 자동 처리
}
```

**언제 WebFlux?**
```
✅ WebSocket / SSE 실시간 스트리밍
✅ MSA 간 대량 HTTP 통신
✅ 동시 접속 수천~수만

❌ 일반 CRUD (JPA 호환 안 됨 — R2DBC 필요)
❌ 복잡한 트랜잭션
❌ 팀원 MVC 경험자 다수

내 프로젝트: MVC + WebClient 조합이 현재 규모에 적합 ✅
```

