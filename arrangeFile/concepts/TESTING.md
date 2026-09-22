# 테스트 코드 — 처음부터 완전 정리

> "테스트 코드를 왜, 어떤 순서로, 어떤 도구로 짜는가"를 처음부터 정리.
> 우리 프로젝트의 `OrderServiceTest.java`를 실전 예시로 계속 참조함.

---

## 목차

1. [테스트가 왜 필요한가](#1-테스트가-왜-필요한가)
2. [테스트의 종류 — 우리 프로젝트는 어디에 해당하나](#2-테스트의-종류--우리-프로젝트는-어디에-해당하나)
3. [테스트 클래스 구조 설계](#3-테스트-클래스-구조-설계)
4. [테스트 하나를 짜는 순서 (사고 흐름)](#4-테스트-하나를-짜는-순서-사고-흐름)
5. [JUnit5 핵심 애노테이션](#5-junit5-핵심-애노테이션)
6. [Mockito 핵심 기능](#6-mockito-핵심-기능)
7. [AssertJ — 검증 문법](#7-assertj--검증-문법)
8. [요즘 테스트 코드 스타일 트렌드](#8-요즘-테스트-코드-스타일-트렌드)
9. [자주 하는 실수 / 안티패턴](#9-자주-하는-실수--안티패턴)
10. [실전 예시로 흐름 복습 — OrderServiceTest](#10-실전-예시로-흐름-복습--orderservicetest)
11. [다음 연습 과제](#11-다음-연습-과제)

---

## 1. 테스트가 왜 필요한가

코드를 수정할 때마다 브라우저 켜서 로그인하고, 장바구니 담고, 주문하고… 이 과정을 손으로 반복하는 건 느리고 사람이 실수함. 테스트 코드는 이걸 **자동화된 확인 스크립트**로 만들어두는 것.

- 리팩토링할 때 "내가 방금 뭔가 망가뜨렸나?"를 몇 초 안에 확인 가능
- 버그를 고칠 때, 그 버그가 재발하지 않는지 증명하는 근거가 됨
- 면접에서 "테스트 커버리지 신경 썼다"는 건 "이 사람이 혼자 대충 짜지 않고, 검증 습관이 있다"는 신호로 읽힘

---

## 2. 테스트의 종류 — 우리 프로젝트는 어디에 해당하나

| 종류 | 무엇을 검증 | 속도 | 우리 프로젝트 예시 |
|---|---|---|---|
| **단위 테스트 (Unit Test)** | 클래스 하나(주로 Service)의 로직만, DB/네트워크 없이 | 매우 빠름 (ms 단위) | `OrderServiceTest`, `PaymentServiceTest`, `MemberAccountServiceTest` — 전부 `@Mock`으로 의존성을 가짜로 대체 |
| **통합 테스트 (Integration Test)** | 여러 계층이 실제로 붙어서 동작하는지 (실제 DB 포함) | 느림 (초 단위) | `@DataJpaTest`, `@SpringBootTest` — 아직 우리 프로젝트엔 없음 |
| **E2E 테스트** | 브라우저/HTTP로 실제 요청 → 응답까지 | 매우 느림 | Postman으로 수동 시나리오 테스트한 것과 유사한 개념 (자동화는 안 함) |

지금 우리가 가진 3개 테스트 클래스는 전부 **단위 테스트**야. `@ExtendWith(MockitoExtension.class)`가 그 표시 — Spring 컨테이너를 아예 안 띄우고 Mockito만 써서, DB 연결 없이 순수 자바 객체 수준에서 로직만 검증함. 그래서 빠르고, "지금 우선순위로는 이거면 충분"함 (실무에서도 서비스 로직 단위 테스트가 가장 기본이자 가장 많이 씀).

---

## 3. 테스트 클래스 구조 설계

### 3-1. 파일 위치 — src/main을 그대로 미러링

```
src/main/java/JOO/jooshop/order/service/OrderService.java
src/test/java/JOO/jooshop/order/service/OrderServiceTest.java   ← 똑같은 패키지 경로 + Test 접미사
```

이게 관례. 테스트 대상 클래스와 같은 패키지 경로에, 이름 뒤에 `Test`만 붙임. IDE가 자동으로 이 규칙을 따라서 파일 만들어줌 (IntelliJ에서 `Ctrl+Shift+T`).

### 3-2. 클래스 안 구조 — 위에서 아래 순서

```java
@ExtendWith(MockitoExtension.class)   // ① Mockito 사용 선언
class OrderServiceTest {

    @Mock private CartRepository cartRepository;          // ② 의존성 가짜 객체 선언
    @Mock private OrderRepository orderRepository;
    @Mock private MemberAccountService memberAccountService;

    @InjectMocks private OrderService orderService;        // ③ 테스트 대상 (실제 객체, 의존성만 가짜로 주입됨)

    @AfterEach
    void clearSecurityContext() { ... }                     // ④ 매 테스트 끝나고 정리할 것

    @Nested
    @DisplayName("주문 확정")
    class ConfirmOrder {                                     // ⑤ 기능별로 묶은 그룹
        @Test
        void confirmOrder_success_savesOrder() { ... }       // ⑥ 실제 테스트 케이스들
        @Test
        void confirmOrder_emptyCart_throwsException() { ... }
    }

    private void authenticateAs(...) { ... }                 // ⑦ 여러 테스트가 공유하는 헬퍼 메서드
}
```

**왜 이 순서인가**: 필드(의존성 선언) → 공통 훅(`@BeforeEach`/`@AfterEach`) → 실제 테스트 케이스 → 마지막에 헬퍼 메서드. 위에서 아래로 읽으면 "무엇을 가지고, 무엇을 검증하는지"가 자연스럽게 드러나게 배치하는 거야.

### 3-3. `@Nested`로 기능별 그룹 짓기

메서드가 하나여도 시나리오는 여러 개(정상/예외/경계) 나오니까, `@Nested` 클래스로 "무슨 기능을 테스트하는 그룹인지" 묶어줘.

```java
@Nested
@DisplayName("주문 확정")
class ConfirmOrder { ... }

@Nested
@DisplayName("주문내역 조회")
class GetMyOrders { ... }
```

실행 결과 리포트에 `주문 확정 > 정상 주문 시 Cart 조회 후 Orders가 DB에 저장된다`처럼 계층적으로 나와서, 테스트가 몇십 개로 늘어나도 뭘 검증하는지 한눈에 보임.

### 3-4. 테스트 메서드 이름 짓는 법

```java
void confirmOrder_success_savesOrder()
void confirmOrder_emptyCart_throwsException()
void confirmOrder_memberIdMismatch_throwsSecurityException()
```

패턴: **`메서드명_조건_기대결과`**. 이름만 읽어도 "뭘 테스트하는지" 알 수 있어야 함. `@DisplayName("정상 주문 시 Cart 조회 후 Orders가 DB에 저장된다")`처럼 한글 설명을 따로 달아주면 실행 리포트가 사람이 읽는 문장이 됨 — 메서드명은 영어로 짧게, `@DisplayName`은 한글로 자세히, 이렇게 역할을 나누는 게 요즘 관례.

---

## 4. 테스트 하나를 짜는 순서 (사고 흐름)

코드부터 치지 말고, 이 순서로 생각하는 게 핵심:

1. **테스트 대상 메서드를 하나 정한다** — 예: `OrderService.confirmOrder(OrderDto)`
2. **시나리오를 말로 먼저 나열한다** (코드 없이 텍스트로):
   - 정상 케이스: 장바구니에 상품 있고, 로그인 정보 맞으면 → 주문이 저장된다
   - 예외 케이스 1: 장바구니가 비어있으면 → `IllegalArgumentException`
   - 예외 케이스 2: 로그인한 사람과 요청한 memberId가 다르면 → `SecurityException`
3. **시나리오 하나당 테스트 메서드 하나**를 만든다 (이름부터 씀, 내용은 비워둬도 됨)
4. 메서드 안에서 **given → when → then** 순서로 채운다:
   - **given**: 이 시나리오가 성립하려면 어떤 데이터/상태가 필요한가 (Mock 준비)
   - **when**: 실제 대상 메서드를 딱 한 번 호출
   - **then**: 결과값 검증 + (필요하면) Mock이 몇 번 불렸는지 검증
5. 실행해서 **의도한 대로 통과/실패하는지** 확인 (특히 예외 테스트는 일부러 given을 틀리게 넣어서 "진짜 저 이유로 실패하는지"도 한 번 확인해보면 좋음)

**핵심 원칙 — 테스트 하나는 "하나의 관심사"만 검증**: `confirmOrder_success_savesOrder()` 안에 "저장도 되고, 로그도 찍히고, 이메일도 가고" 다 넣지 말고, 관심사별로 테스트를 쪼개는 게 좋음. 실패했을 때 "정확히 뭐가 깨졌는지" 바로 알 수 있게.

---

## 5. JUnit5 핵심 애노테이션

| 애노테이션 | 역할 |
|---|---|
| `@Test` | 이 메서드가 테스트 케이스라는 표시 |
| `@BeforeEach` | 각 테스트 실행 **전**에 매번 실행 (공통 준비) |
| `@AfterEach` | 각 테스트 실행 **후**에 매번 실행 (공통 정리 — 우리 프로젝트의 `clearSecurityContext()`) |
| `@BeforeAll` / `@AfterAll` | 클래스 전체에서 딱 1번만 (static 메서드여야 함) — 무거운 리소스 준비할 때 |
| `@DisplayName` | 테스트 이름을 사람이 읽는 문장으로 표시 |
| `@Nested` | 관련 테스트를 그룹으로 묶는 내부 클래스 |
| `@Disabled` | 일시적으로 테스트 끄기 (이유를 꼭 남길 것: `@Disabled("결제 API 연동 대기중")`) |
| `@ParameterizedTest` + `@ValueSource`/`@CsvSource` | 같은 로직을 값만 바꿔가며 여러 번 (아직 우리 프로젝트엔 없지만 자주 씀 — 아래 예시) |

**`@ParameterizedTest` 예시** (경계값/여러 입력을 한 번에 검증할 때):

```java
@ParameterizedTest
@ValueSource(ints = {0, -1, -100})
@DisplayName("수량이 1 미만이면 예외 발생")
void changeQuantity_invalidQuantity_throwsException(int invalidQuantity) {
    assertThatThrownBy(() -> cart.changeQuantity(invalidQuantity))
            .isInstanceOf(IllegalArgumentException.class);
}
```

이러면 0, -1, -100 세 값 각각에 대해 테스트가 3번 따로 돌아감. 테스트 메서드를 3개 복붙하는 대신 한 번에.

---

## 6. Mockito 핵심 기능

### 6-1. Mock을 만드는 두 가지 방법

```java
@Mock
private CartRepository cartRepository;   // 필드로 선언 — 클래스 전체에서 재사용

Cart cart = mock(Cart.class);            // 메서드 안에서 즉석으로 — 그 테스트에서만 필요할 때
```

### 6-2. `@InjectMocks` — 진짜 객체 + 가짜 의존성

```java
@InjectMocks
private OrderService orderService;
```

`OrderService` 자체는 **진짜 객체**로 만들어짐 (로직이 실제로 실행됨). 단지 생성자로 받는 `CartRepository`, `OrderRepository`, `MemberAccountService`만 위에서 만든 `@Mock`들이 자동으로 꽂힘. "테스트 대상은 진짜, 그 대상이 의존하는 바깥 세상(DB 등)만 가짜"가 핵심.

### 6-3. `given(...).willReturn(...)` — 가짜 객체 길들이기 (Stubbing)

```java
given(cartRepository.findAllById(orderDto.getCartIds())).willReturn(List.of(cart));
```

"이 메서드가 이 입력으로 불리면, 이 값을 리턴해라"를 미리 가르쳐두는 것. `BDDMockito`의 `given/willReturn`과 순수 `Mockito`의 `when/thenReturn`은 완전히 동일한 기능, 문체만 다름 (요즘은 가독성 때문에 `given/willReturn` 스타일을 더 권장하는 편).

**예외를 던지게 하고 싶을 때**:

```java
given(iamportClient.cancelPaymentByImpUid(any())).willThrow(new IamportResponseException("실패"));
```

### 6-4. `verify(...)` — 진짜 호출됐는지 사후 검증

```java
verify(orderRepository, times(1)).save(any(Orders.class));   // 정확히 1번 불렸다
verify(orderRepository, never()).save(any(Orders.class));    // 한 번도 안 불렸다
verify(emailMemberService, atLeastOnce()).sendEmailVerification(anyString()); // 최소 1번
```

`given`이 "입력 → 출력 세팅"이라면 `verify`는 "실제로 그 메서드가 불렸는지" 확인. **결과값(리턴값)보다 "부수효과(호출 여부)"가 중요한 경우에 씀** — 예: 저장은 됐는지, 이메일은 발송됐는지.

### 6-5. Argument Matcher — `any()`, `anyString()`, `eq()`

```java
verify(orderRepository).save(any(Orders.class));   // Orders 타입이면 값 상관없이
given(memberRepository.existsByEmail(anyString())).willReturn(false); // 어떤 문자열이든
given(paymentRepository.findAllByMember_Id(memberId)).willReturn(...); // 특정 값만 (matcher 없이 그냥 값)
```

주의: 한 메서드 호출에서 matcher(`any()` 등)를 하나라도 쓰면, **나머지 인자도 전부 matcher로 통일**해야 함 (섞어 쓰면 `InvalidUseOfMatchersException`).

### 6-6. `ArgumentCaptor` — 어떤 값으로 호출됐는지 직접 꺼내보기

```java
ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
verify(orderRepository).save(captor.capture());
assertThat(captor.getValue().getTotalPrice()).isEqualTo(BigDecimal.valueOf(20000));
```

"저장이 됐다"만이 아니라 "**어떤 내용으로** 저장됐는지"까지 검증하고 싶을 때. 리턴값을 안 쓰는 메서드(`void save(...)` 같은)의 내부 상태를 확인할 때 특히 유용.

### 6-7. Strict Stubbing 주의 — `UnnecessaryStubbingException`

`PaymentServiceTest`의 주석에서 이미 겪었던 문제: **given으로 설정했는데 실제로 안 쓰인 stub이 있으면** Mockito가 기본적으로 에러를 냄 (엄격 모드). "이 테스트에 불필요한 세팅이 남아있다"는 걸 알려주는 기능이야 — 지저분한 테스트를 막아주는 안전장치라고 생각하면 됨. 해결법은 그 테스트에서 진짜 필요한 stub만 남기는 것 (프로젝트에서 이미 이렇게 처리했음).

---

## 7. AssertJ — 검증 문법

```java
assertThat(result).isNotNull();
assertThat(result.getOrderProducts()).hasSize(1);
assertThat(member.getEmail()).isEqualTo("test@example.com");
assertThat(list).isEmpty();
assertThat(number).isGreaterThan(0);

assertThatThrownBy(() -> orderService.confirmOrder(orderDto))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("주문할 장바구니 항목이 없습니다");
```

`assertThat(...)` 뒤에 점(`.`)으로 계속 이어붙이는 **메서드 체이닝** 방식이라, 영어 문장처럼 읽힘("assert that result is not null"). JUnit 기본 `assertEquals(expected, actual)`보다 읽기 좋아서 요즘은 거의 AssertJ를 기본으로 씀.

---

## 8. 요즘 테스트 코드 스타일 트렌드

우리 프로젝트 테스트 코드가 이미 아래 트렌드를 잘 따르고 있어서, 그대로 표준으로 삼아도 됨.

1. **Given-When-Then 주석을 명시적으로 남긴다** — 팀마다 다르지만 최근엔 주석으로라도 구간을 나누는 걸 선호 (가독성)
2. **테스트 메서드 이름은 영어로 짧게, `@DisplayName`은 한글 문장으로** — 리포트 가독성과 코드 관례를 분리
3. **`@Nested`로 시나리오 묶기** — 메서드 하나에 테스트가 몰리지 않게, 기능 단위로 계층화
4. **Test Fixture는 private 헬퍼 메서드로 분리** — `createMember()`, `createOrder()`, `authenticateAs()`처럼, "테스트 데이터 만드는 코드"와 "검증 로직"을 분리 (`PaymentServiceTest`가 이미 이렇게 함)
5. **Mock보다 실제 객체를 우선한다** — DTO나 값 객체처럼 로직이 단순한 건 `mock()`으로 흉내내지 말고 실제로 생성 (`Member.registerGeneral(...)`처럼). Mock은 "DB/외부 API처럼 진짜로 대체 불가능한 것"에만 최소로 씀. 과도한 mocking은 오히려 테스트를 실제 동작과 멀어지게 만듦
6. **부정 케이스(예외/실패)를 정상 케이스만큼 챙긴다** — `OrderServiceTest`도 정상 1개 + 예외 2개로 구성된 것처럼, "이게 실패해야 하는 상황"도 반드시 테스트
7. **한 테스트 = 한 관심사, assert는 관련된 것끼리만 묶는다** — 너무 많은 것을 한 테스트에 우겨넣지 않기
8. **F.I.R.S.T 원칙** (오래됐지만 여전히 유효한 기준):
   - **F**ast — 빨라야 함 (그래서 단위 테스트는 DB 없이 Mock으로)
   - **I**ndependent — 테스트끼리 순서/상태 의존 없어야 함 (`@AfterEach`로 SecurityContext 정리하는 이유)
   - **R**epeatable — 어느 환경에서 돌려도 같은 결과
   - **S**elf-validating — 사람이 로그 읽고 판단하는 게 아니라, pass/fail이 자동으로 나와야 함
   - **T**imely — 코드 다 짜고 나중에 몰아서 말고, 기능 만들면서 같이

---

## 9. 자주 하는 실수 / 안티패턴

| 실수 | 왜 문제인가 | 해결 |
|---|---|---|
| 테스트 하나에 assert를 10개씩 몰아넣기 | 실패해도 어디가 문제인지 바로 안 보임 | 관심사별로 테스트 쪼개기 |
| Mock을 과하게 써서 모든 걸 가짜로 | 실제 로직이 하나도 안 돌아가고, 껍데기만 검증하는 테스트가 됨 | 로직이 있는 객체는 최대한 진짜로 생성 |
| `@AfterEach` 정리 안 함 (전역 상태 남김) | 테스트 순서에 따라 결과가 달라지는 "flaky test" 발생 | `SecurityContextHolder.clearContext()`처럼 항상 원상복구 |
| 테스트 이름이 `test1()`, `test2()` | 실패해도 뭘 검증하다 실패했는지 이름만으론 모름 | `메서드_조건_결과` 패턴 |
| given 세팅해놓고 실제로 안 씀 | Mockito가 `UnnecessaryStubbingException`으로 알려줌 | 진짜 필요한 stub만 남기기 |
| 예외 테스트를 안 씀, 정상 케이스만 테스트 | 실제 버그는 대부분 예외/경계 상황에서 남 | 실패 시나리오를 정상 케이스만큼 챙기기 |

---

## 10. 실전 예시로 흐름 복습 — OrderServiceTest

```java
@Test
@DisplayName("정상 주문 시 Cart 조회 후 Orders가 DB에 저장된다")
void confirmOrder_success_savesOrder() {
    // given — 이 시나리오가 성립하려면 필요한 것들
    Long memberId = 1L;
    authenticateAs(memberId, MemberRole.USER);              // 로그인 상태 흉내
    Member member = Member.registerGeneral(...);            // 실제 객체 (로직 단순해서 mock 안 씀)
    Cart cart = mock(Cart.class);                            // 외부(DB에서 온 것 흉내)라 mock
    given(cart.getProductVariant()).willReturn(pm);
    given(cartRepository.findAllById(...)).willReturn(List.of(cart));
    given(memberAccountService.findMemberById(memberId)).willReturn(member);
    given(orderRepository.save(any(Orders.class))).willAnswer(invocation -> invocation.getArgument(0));

    // when — 대상 메서드 딱 한 번 호출
    Orders result = orderService.confirmOrder(orderDto);

    // then — 결과 + 부수효과 검증
    assertThat(result).isNotNull();
    assertThat(result.getOrderProducts()).hasSize(1);
    verify(orderRepository, times(1)).save(any(Orders.class));
}
```

이 순서 그대로: **① 무슨 상황을 만들지 결정 → ② 그 상황에 필요한 Mock만 최소로 세팅 → ③ 대상 메서드 호출 → ④ 결과값 + 호출 여부 검증.** 다음에 새 테스트 클래스 짤 때 이 4단계만 기억하면 됨.

---

## 11. 다음 연습 과제

지금 배운 걸로 바로 연습해볼 만한 것 (난이도 낮은 순):

1. **`CartService` 단위 테스트** — `OrderServiceTest`랑 패턴 거의 동일 (장바구니 추가/수량 변경/삭제), 지금 배운 걸 복붙 수준으로 적용 가능
2. **`Cart` 엔티티 자체의 도메인 로직 테스트** — `changeQuantity()`가 0 이하일 때 예외 던지는지, `@ParameterizedTest` 써서 여러 값으로 (5번 항목 예시 그대로 적용 가능)
3. **`OrderService.getMyOrders()` 테스트** — 지금 없는 걸 하나 추가해보기, `confirmOrder`보다 훨씬 단순함 (Repository 호출 → 매핑만 검증)

3번부터 직접 짜보고 막히면 같이 봐줄게.
