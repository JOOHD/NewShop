package JOO.jooshop.payment.service;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.exception.customException.OrderNotFoundException;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.model.PaymentRequestDto;
import JOO.jooshop.payment.repository.PaymentRefundRepository;
import JOO.jooshop.payment.repository.PaymentRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productVariant.entity.ProductVariant;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.response.Payment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * PaymentService 단위 테스트.
 *
 * [리팩토링 반영]
 * - processPaymentDone / getPaymentHistoriesByMemberId는 내부에서
 *   MemberAuthorizationUtil.verifyUserIdMatch()를 호출하므로,
 *   SecurityContext에 로그인 사용자를 주입해야 SecurityException 없이 통과함 (OrderServiceTest와 동일 패턴)
 * - PaymentRequestDto는 merchantUid 필드가 없음 (memberId/orderId/price/inventoryIdList 4개) → 생성자 인자 수 수정
 * - processPaymentDone은 order.getOrderProducts()를 순회하며 PaymentHistory를 저장하므로,
 *   테스트용 Orders에 OrderProduct를 최소 1개 실제로 추가해야 save()가 호출됨
 *
 * 핵심 검증 포인트:
 * 1. 결제 완료 처리 — PaymentHistory 저장
 *    (예전엔 여기서 Redis 임시 데이터(cartIds/tempOrder)도 삭제했지만, 그 키는 지금 구조에서
 *     애초에 아무도 저장하지 않는 옛날 Redis 2단계 주문 흐름의 잔재라 로직/테스트 둘 다 제거함)
 * 2. 결제 취소 — 취소 불가 상태 방어, Iamport 취소 API 호출
 * 3. 결제 이력 조회
 *
 * IamportClient는 외부 API이므로 Mock으로 대체.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentRefundRepository paymentRefundRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MemberAccountService memberAccountService;

    @Mock
    private IamportClient iamportClient;

    @InjectMocks
    private PaymentService paymentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ========================================================
    // 결제 완료 처리
    // ========================================================
    @Nested
    @DisplayName("결제 완료 처리")
    class ProcessPayment {

        @Test
        @DisplayName("정상 결제 완료 시 PaymentHistory가 저장된다")
        void processPaymentDone_success() {
            // given
            Long memberId = 1L;
            Long orderId = 10L;
            authenticateAs(memberId, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(50000)));

            Payment iamportPayment = mockIamportPayment("imp_test_uid");
            PaymentRequestDto request = new PaymentRequestDto(memberId, orderId, BigDecimal.valueOf(50000), List.of(1L));

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(memberAccountService.findMemberById(memberId)).willReturn(member);

            // when
            paymentService.processPaymentDone(iamportPayment, request);

            // then
            verify(paymentRepository, atLeastOnce()).save(any(PaymentHistory.class));
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 결제 시 예외 발생")
        void processPaymentDone_orderNotFound_throwsException() {
            // given
            Long memberId = 1L;
            authenticateAs(memberId, MemberRole.USER);

            // 주문 조회 단계에서 바로 예외가 나서 Payment의 값은 전혀 읽히지 않으므로 스텁 없는 순수 mock 사용
            // (미리 stub 해두면 전부 안 쓰여서 Mockito strict-stub이 UnnecessaryStubbingException을 던짐)
            Payment iamportPayment = mock(Payment.class);
            PaymentRequestDto request = new PaymentRequestDto(memberId, 999L, BigDecimal.valueOf(50000), List.of());

            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.processPaymentDone(iamportPayment, request))
                    .isInstanceOf(OrderNotFoundException.class);

            // 저장이 호출되지 않음을 검증
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("남의 주문에 결제 완료 처리를 요청하면 거부된다")
        void processPaymentDone_otherUsersOrder_throwsSecurityException() {
            // given — 주문 주인은 1번 회원, 요청·로그인은 2번 회원
            authenticateAs(2L, MemberRole.USER);

            Member owner = createMember(); // id = 1
            Orders order = createOrder(owner);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(50000)));

            Payment iamportPayment = mock(Payment.class);
            PaymentRequestDto request = new PaymentRequestDto(2L, 10L, BigDecimal.valueOf(50000), List.of());

            given(orderRepository.findById(10L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> paymentService.processPaymentDone(iamportPayment, request))
                    .isInstanceOf(SecurityException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("실제 결제 금액이 주문 금액과 다르면 거부된다 (100원 결제 후 주문 완료 처리 방지)")
        void processPaymentDone_amountMismatch_throwsException() {
            // given — 주문 금액 50,000원, Iamport 실제 결제 금액 100원
            authenticateAs(1L, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(50000)));

            Payment iamportPayment = mock(Payment.class);
            given(iamportPayment.getImpUid()).willReturn("imp_cheap");
            given(iamportPayment.getAmount()).willReturn(BigDecimal.valueOf(100));
            // 클라이언트가 보낸 금액도 100원으로 맞춰 보내는 경우를 가정
            PaymentRequestDto request = new PaymentRequestDto(1L, 10L, BigDecimal.valueOf(100), List.of());

            given(orderRepository.findById(10L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> paymentService.processPaymentDone(iamportPayment, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문 금액과 일치하지 않습니다");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 처리된 imp_uid로 다시 요청하면 중복 저장 없이 넘어간다 (멱등성)")
        void processPaymentDone_duplicateImpUid_isIgnored() {
            // given
            authenticateAs(1L, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(50000)));

            Payment iamportPayment = mock(Payment.class);
            given(iamportPayment.getImpUid()).willReturn("imp_dup");
            PaymentRequestDto request = new PaymentRequestDto(1L, 10L, BigDecimal.valueOf(50000), List.of());

            given(orderRepository.findById(10L)).willReturn(Optional.of(order));
            given(paymentRepository.existsByImpUid("imp_dup")).willReturn(true);

            // when
            paymentService.processPaymentDone(iamportPayment, request);

            // then — 이력이 중복 저장되지 않는다
            verify(paymentRepository, never()).save(any());
        }
    }

    // ========================================================
    // 결제 취소
    // ========================================================
    @Nested
    @DisplayName("결제 취소")
    class CancelPayment {

        @Test
        @DisplayName("이미 취소된 결제는 재취소할 수 없다")
        void cancelPayment_alreadyCanceled_throwsException() {
            // given — 결제 주인(1번 회원)이 로그인한 상태
            authenticateAs(1L, MemberRole.USER);
            PaymentHistory history = createCanceledPaymentHistory();
            given(paymentRepository.findById(1L)).willReturn(Optional.of(history));

            // when & then
            assertThatThrownBy(() ->
                    paymentService.cancelPayment(1L, null, iamportClient)
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("이미 취소되었거나 취소할 수 없는 결제입니다");

            // Iamport API가 호출되지 않음을 검증 (외부 API 불필요 호출 방지)
            verifyNoInteractions(iamportClient);
        }

        @Test
        @DisplayName("남의 결제를 취소하려 하면 거부되고 Iamport 취소 API도 호출되지 않는다")
        void cancelPayment_otherUsersPayment_throwsSecurityException() {
            // given — 결제 주인은 1번 회원, 로그인은 2번 회원
            authenticateAs(2L, MemberRole.USER);
            PaymentHistory history = createPaymentHistoryOf1();
            given(paymentRepository.findById(1L)).willReturn(Optional.of(history));

            // when & then
            assertThatThrownBy(() ->
                    paymentService.cancelPayment(1L, null, iamportClient)
            ).isInstanceOf(SecurityException.class);

            verifyNoInteractions(iamportClient);
        }

        @Test
        @DisplayName("존재하지 않는 결제 이력 취소 시 예외 발생")
        void cancelPayment_historyNotFound_throwsException() {
            given(paymentRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    paymentService.cancelPayment(999L, null, iamportClient)
            ).isInstanceOf(RuntimeException.class); // PaymentHistoryNotFoundException
        }
    }

    // ========================================================
    // 결제 이력 조회
    // ========================================================
    @Nested
    @DisplayName("결제 이력 조회")
    class GetPaymentHistory {

        @Test
        @DisplayName("memberId로 결제 이력 조회 시 해당 회원의 이력만 반환된다")
        void getPaymentHistoriesByMemberId_returnsMemberHistories() {
            // given
            Long memberId = 1L;
            authenticateAs(memberId, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);

            PaymentHistory h1 = PaymentHistory.createPaymentHistory(
                    member, order, createOrderProduct(BigDecimal.valueOf(30000)), "imp_001", "card",
                    BigDecimal.valueOf(30000), null, null, "서울", "test@test.com"
            );
            PaymentHistory h2 = PaymentHistory.createPaymentHistory(
                    member, order, createOrderProduct(BigDecimal.valueOf(50000)), "imp_002", "card",
                    BigDecimal.valueOf(50000), null, null, "부산", "test@test.com"
            );

            given(paymentRepository.findAllByMember_Id(memberId)).willReturn(List.of(h1, h2));

            // when
            var result = paymentService.getPaymentHistoriesByMemberId(memberId);

            // then
            assertThat(result).hasSize(2);
        }
    }

    // ========================================================
    // 헬퍼 메서드
    // ========================================================
    private Member createMember() {
        Member member = Member.registerGeneral(
                "test@example.com", "encodedPw",
                "홍길동", "길동이", "010-1234-5678", "uuid"
        );
        // registerGeneral()은 DB 저장 전이라 id가 null → 테스트에서 memberId(1L) 기준으로
        // 검증하는 다른 로직들과 맞추기 위해 강제로 세팅
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    /**
     * 실제 Orders.createOrder(8개 인자) 팩토리로 주문만 생성.
     * OrderProduct 부착은 필요한 테스트(processPaymentDone_success)에서만 개별적으로 함 —
     * 여기서 항상 붙이면 그 OrderProduct의 productVariant.getProduct() 스텁이
     * 다른 테스트(cancelPayment 등)에서는 아무도 호출하지 않아
     * Mockito strict-stub 모드에서 UnnecessaryStubbingException이 남.
     */
    private Orders createOrder(Member member) {
        return Orders.createOrder(
                member,
                "홍길동",
                "010-1234-5678",
                "12345",
                "서울시 강남구",
                null,
                PayMethod.card,
                "order_test_" + UUID.randomUUID()
        );
    }

    /**
     * PaymentHistory.createPaymentHistory()는 orderProduct == null이면
     * IllegalArgumentException("주문 상품은 필수입니다.")을 던지므로,
     * 결제 이력용 테스트 데이터를 만들 때도 항상 실제 OrderProduct를 넘겨야 함.
     */
    private OrderProduct createOrderProduct(BigDecimal priceAtOrder) {
        ProductVariant productVariant = mock(ProductVariant.class);
        // PaymentHistoryDto.from()이 paymentHistory.getProduct().getProductId()/isDiscount()를
        // 그대로 호출하므로, Product까지 실제 값으로 연결해줘야 NPE 없이 동작함
        // lenient: 결제 이력을 만들지 않는 테스트(주문만 필요한 검증 테스트)에서는 호출되지 않아도 되도록
        lenient().when(productVariant.getProduct()).thenReturn(Product.ofId(1L));

        return OrderProduct.createOrderProduct(
                productVariant,
                "테스트 상품",
                "M",
                null,
                priceAtOrder,
                1
        );
    }

    /**
     * Iamport Payment Mock 생성.
     * 외부 API 응답을 Mockito로 대체하여 실제 Iamport 서버 없이 테스트.
     *
     * processPaymentDone()이 실제로 읽는 필드만 스텁함(getImpUid/getPayMethod/getBuyerAddr/getBuyerEmail).
     * getAmount()/getMerchantUid()는 프로덕션 코드에서 안 읽어서 스텁해도 항상 unused —
     * Mockito strict-stub 모드에서 UnnecessaryStubbingException 원인이라 제거함.
     */
    private Payment mockIamportPayment(String impUid) {
        Payment payment = mock(Payment.class);
        given(payment.getImpUid()).willReturn(impUid);
        given(payment.getPayMethod()).willReturn("card");
        given(payment.getBuyerAddr()).willReturn("서울시 강남구");
        given(payment.getBuyerEmail()).willReturn("test@example.com");
        given(payment.getAmount()).willReturn(BigDecimal.valueOf(50000)); // 주문 금액과 같은 정상 결제
        return payment;
    }

    // 1번 회원의 취소 가능한(정상 결제) 결제 이력
    private PaymentHistory createPaymentHistoryOf1() {
        Member member = createMember();
        Orders order = createOrder(member);
        return PaymentHistory.createPaymentHistory(
                member, order, createOrderProduct(BigDecimal.valueOf(30000)), "imp_ok", "card",
                BigDecimal.valueOf(30000), null, null, "서울", "test@test.com"
        );
    }

    private PaymentHistory createCanceledPaymentHistory() {
        Member member = createMember();
        Orders order = createOrder(member);
        PaymentHistory history = PaymentHistory.createPaymentHistory(
                member, order, createOrderProduct(BigDecimal.valueOf(30000)), "imp_canceled", "card",
                BigDecimal.valueOf(30000), null, null, "서울", "test@test.com"
        );
        history.markCanceled(); // 이미 취소 상태로 설정
        return history;
    }

    // ========================================================
    // 헬퍼 — SecurityContext에 로그인 사용자 주입 (OrderServiceTest와 동일 패턴)
    // ========================================================
    private void authenticateAs(Long memberId, MemberRole role) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        given(userDetails.getMemberId()).willReturn(memberId);
        given(userDetails.getMemberRole()).willReturn(role);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
