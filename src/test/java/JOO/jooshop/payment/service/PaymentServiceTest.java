package JOO.jooshop.payment.service;

import JOO.jooshop.cart.entity.Cart;
import JOO.jooshop.cart.repository.CartRepository;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.exception.customException.OrderNotFoundException;
import JOO.jooshop.global.exception.customException.OrderNotPayableException;
import JOO.jooshop.global.exception.customException.PaymentAmountMismatchException;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.entity.PaymentRecord;
import JOO.jooshop.payment.entity.PaymentRefund;
import JOO.jooshop.payment.entity.PaymentStatus;
import JOO.jooshop.payment.model.PaymentCancelDto;
import JOO.jooshop.payment.model.PaymentRequestDto;
import JOO.jooshop.payment.repository.PaymentRecordRepository;
import JOO.jooshop.payment.repository.PaymentRefundRepository;
import JOO.jooshop.payment.repository.PaymentRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.productVariant.entity.ProductVariant;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
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
    private PaymentRecordRepository paymentRecordRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

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

            Payment iamportPayment = mockIamportPayment("imp_test_uid", order.getMerchantUid());
            PaymentRequestDto request = new PaymentRequestDto(memberId, orderId, BigDecimal.valueOf(50000), List.of(1L));

            given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
            given(memberAccountService.findMemberById(memberId)).willReturn(member);

            // when
            paymentService.processPaymentDone(iamportPayment, request);

            // then
            verify(paymentRepository, atLeastOnce()).save(any(PaymentHistory.class));
            // 결제 1건 = 1행 기록(imp_uid UNIQUE)도 함께 저장되고, 주문은 결제 완료 상태가 된다
            verify(paymentRecordRepository).save(any(PaymentRecord.class));
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETE);
        }

        @Test
        @DisplayName("결제가 끝나면 본인 장바구니 중 이 주문에 담긴 옵션의 항목만 삭제하고, 주문과 무관한 항목은 남긴다")
        void processPaymentDone_clearsOnlyPaidOwnCarts() {
            // given — 주문에는 100번 옵션만 있다. 장바구니에는 100번(주문함), 200번(주문 안 함)이 있다.
            Long memberId = 1L;
            Long orderId = 10L;
            authenticateAs(memberId, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(orderProductOf(100L, 1, BigDecimal.valueOf(50000)));

            Cart orderedCart = cartOf(100L);
            Cart otherCart = cartOf(200L);
            given(cartRepository.findOwnedCartsByIds(List.of(1L, 2L), memberId))
                    .willReturn(List.of(orderedCart, otherCart));

            Payment iamportPayment = mockIamportPayment("imp_cart_clear", order.getMerchantUid());
            PaymentRequestDto request = new PaymentRequestDto(memberId, orderId, BigDecimal.valueOf(50000), List.of(1L, 2L));

            given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
            given(memberAccountService.findMemberById(memberId)).willReturn(member);

            // when
            paymentService.processPaymentDone(iamportPayment, request);

            // then — 주문한 옵션의 장바구니만 삭제
            verify(cartRepository).deleteAll(List.of(orderedCart));
        }

        @Test
        @DisplayName("바로 구매(장바구니 ID 없음)는 장바구니를 건드리지 않는다")
        void processPaymentDone_buyNow_doesNotTouchCarts() {
            Long memberId = 1L;
            Long orderId = 10L;
            authenticateAs(memberId, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(50000)));

            Payment iamportPayment = mockIamportPayment("imp_buy_now", order.getMerchantUid());
            PaymentRequestDto request = new PaymentRequestDto(memberId, orderId, BigDecimal.valueOf(50000), List.of());

            given(orderRepository.findByIdForUpdate(orderId)).willReturn(Optional.of(order));
            given(memberAccountService.findMemberById(memberId)).willReturn(member);

            paymentService.processPaymentDone(iamportPayment, request);

            verifyNoInteractions(cartRepository);
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

            given(orderRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

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

            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

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
            given(iamportPayment.getMerchantUid()).willReturn(order.getMerchantUid());
            given(iamportPayment.getImpUid()).willReturn("imp_cheap");
            given(iamportPayment.getAmount()).willReturn(BigDecimal.valueOf(100));
            // 클라이언트가 보낸 금액도 100원으로 맞춰 보내는 경우를 가정
            PaymentRequestDto request = new PaymentRequestDto(1L, 10L, BigDecimal.valueOf(100), List.of());

            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> paymentService.processPaymentDone(iamportPayment, request))
                    .isInstanceOf(PaymentAmountMismatchException.class)
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
            given(iamportPayment.getMerchantUid()).willReturn(order.getMerchantUid());
            given(iamportPayment.getImpUid()).willReturn("imp_dup");
            PaymentRequestDto request = new PaymentRequestDto(1L, 10L, BigDecimal.valueOf(50000), List.of());

            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
            given(paymentRepository.existsByImpUid("imp_dup")).willReturn(true);

            // when
            paymentService.processPaymentDone(iamportPayment, request);

            // then — 이력이 중복 저장되지 않는다
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 주문의 결제(주문번호 불일치)는 거부된다")
        void processPaymentDone_merchantUidMismatch_throwsException() {
            // given — 결제의 주문번호가 이 주문의 주문번호와 다름
            authenticateAs(1L, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(50000)));

            Payment iamportPayment = mock(Payment.class);
            given(iamportPayment.getMerchantUid()).willReturn("order_of_someone_else");
            PaymentRequestDto request = new PaymentRequestDto(1L, 10L, BigDecimal.valueOf(50000), List.of());

            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            // when & then — PaymentAmountMismatch/OrderNotPayable이 아닌 일반 예외 (남의 결제를 자동 취소하지 않기 위함)
            assertThatThrownBy(() -> paymentService.processPaymentDone(iamportPayment, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .isNotInstanceOf(PaymentAmountMismatchException.class)
                    .hasMessageContaining("주문번호가 이 주문과 일치하지 않습니다");
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 결제 완료된 주문에 다른 결제번호로 또 결제하면 거부된다")
        void processPaymentDone_orderAlreadyPaid_throwsException() {
            // given — 주문은 이미 COMPLETE, 새로운 imp_uid로 두 번째 결제가 들어옴
            authenticateAs(1L, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(50000)));
            order.markPaid();

            Payment iamportPayment = mock(Payment.class);
            given(iamportPayment.getMerchantUid()).willReturn(order.getMerchantUid());
            given(iamportPayment.getImpUid()).willReturn("imp_second");
            PaymentRequestDto request = new PaymentRequestDto(1L, 10L, BigDecimal.valueOf(50000), List.of());

            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));

            // when & then
            assertThatThrownBy(() -> paymentService.processPaymentDone(iamportPayment, request))
                    .isInstanceOf(OrderNotPayableException.class);
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("상품 합계가 5만원 미만이면 배송비 5,000원이 포함된 금액으로 검증된다")
        void processPaymentDone_includesDeliveryFee() {
            // given — 상품 30,000원 + 배송비 5,000원 = 35,000원
            authenticateAs(1L, MemberRole.USER);

            Member member = createMember();
            Orders order = createOrder(member);
            order.addOrderProduct(createOrderProduct(BigDecimal.valueOf(30000)));

            Payment iamportPayment = mock(Payment.class);
            given(iamportPayment.getMerchantUid()).willReturn(order.getMerchantUid());
            given(iamportPayment.getImpUid()).willReturn("imp_fee");
            given(iamportPayment.getAmount()).willReturn(BigDecimal.valueOf(35000));
            given(iamportPayment.getPayMethod()).willReturn("card");
            given(iamportPayment.getBuyerAddr()).willReturn("서울");
            given(iamportPayment.getBuyerEmail()).willReturn("test@example.com");
            PaymentRequestDto request = new PaymentRequestDto(1L, 10L, BigDecimal.valueOf(35000), List.of());

            given(orderRepository.findByIdForUpdate(10L)).willReturn(Optional.of(order));
            given(memberAccountService.findMemberById(1L)).willReturn(member);

            // when
            paymentService.processPaymentDone(iamportPayment, request);

            // then
            assertThat(order.getPayAmount()).isEqualByComparingTo("35000");
            verify(paymentRepository, atLeastOnce()).save(any(PaymentHistory.class));
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
        @DisplayName("결제를 취소하면 같은 결제의 모든 상품 이력, 결제 기록, 주문 상태가 함께 취소된다")
        @SuppressWarnings("unchecked")
        void cancelPayment_success_cancelsWholePayment() throws Exception {
            // given — 한 번의 결제(imp_ok)에 상품 2줄의 이력이 있고, 주문은 결제 완료 상태
            authenticateAs(1L, MemberRole.USER);
            PaymentHistory history = createPaymentHistoryOf1();
            Orders order = history.getOrders();
            order.markPaid();
            PaymentHistory sibling = PaymentHistory.createPaymentHistory(
                    history.getMember(), order, createOrderProduct(BigDecimal.valueOf(30000)), "imp_ok", "card",
                    BigDecimal.valueOf(30000), null, null, "서울", "test@test.com");
            PaymentRecord record = PaymentRecord.paid(history.getMember(), order, "imp_ok", "card", BigDecimal.valueOf(30000));

            given(paymentRepository.findById(1L)).willReturn(Optional.of(history));
            given(paymentRepository.findAllByImpUid("imp_ok")).willReturn(List.of(history, sibling));
            given(paymentRecordRepository.findByImpUid("imp_ok")).willReturn(Optional.of(record));

            IamportResponse<Payment> iamportOk = mock(IamportResponse.class);
            given(iamportOk.getCode()).willReturn(0);
            given(iamportClient.cancelPaymentByImpUid(any(CancelData.class))).willReturn(iamportOk);

            PaymentCancelDto dto = new PaymentCancelDto(1L, PayMethod.card, "사이즈 불량", "홍길동", "국민", "123");

            // when
            paymentService.cancelPayment(1L, dto, iamportClient);

            // then
            assertThat(history.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(sibling.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(record.getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
            verify(paymentRefundRepository).save(any(PaymentRefund.class));
        }

        @Test
        @DisplayName("결제를 취소하면 주문 때 차감한 재고가 같은 옵션끼리 합산되어 복구된다")
        @SuppressWarnings("unchecked")
        void cancelPayment_restoresStock() throws Exception {
            // given — 100번 옵션 1개 + 2개(같은 옵션), 200번 옵션 4개로 만든 결제 완료 주문
            authenticateAs(1L, MemberRole.USER);
            PaymentHistory history = createPaymentHistoryOf1();
            Orders order = history.getOrders();
            order.addOrderProduct(orderProductOf(100L, 1, BigDecimal.valueOf(10000)));
            order.addOrderProduct(orderProductOf(100L, 2, BigDecimal.valueOf(10000)));
            order.addOrderProduct(orderProductOf(200L, 4, BigDecimal.valueOf(10000)));
            order.markPaid();

            given(paymentRepository.findById(1L)).willReturn(Optional.of(history));
            IamportResponse<Payment> iamportOk = mock(IamportResponse.class);
            given(iamportOk.getCode()).willReturn(0);
            given(iamportClient.cancelPaymentByImpUid(any(CancelData.class))).willReturn(iamportOk);

            PaymentCancelDto dto = new PaymentCancelDto(1L, PayMethod.card, "단순 변심", "홍길동", "국민", "123");

            // when
            paymentService.cancelPayment(1L, dto, iamportClient);

            // then
            verify(productVariantRepository).increaseStock(100L, 3L);   // 1 + 2 합산
            verify(productVariantRepository).increaseStock(200L, 4L);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        }

        @Test
        @DisplayName("PG 취소가 실패하면 주문 상태와 재고는 그대로다")
        @SuppressWarnings("unchecked")
        void cancelPayment_iamportRejected_keepsOrderAndStock() throws Exception {
            authenticateAs(1L, MemberRole.USER);
            PaymentHistory history = createPaymentHistoryOf1();
            Orders order = history.getOrders();
            order.addOrderProduct(orderProductOf(100L, 1, BigDecimal.valueOf(10000)));
            order.markPaid();

            given(paymentRepository.findById(1L)).willReturn(Optional.of(history));
            IamportResponse<Payment> iamportRejected = mock(IamportResponse.class);
            given(iamportRejected.getCode()).willReturn(1);
            given(iamportRejected.getMessage()).willReturn("거부");
            given(iamportClient.cancelPaymentByImpUid(any(CancelData.class))).willReturn(iamportRejected);

            assertThatThrownBy(() -> paymentService.cancelPayment(1L,
                    new PaymentCancelDto(1L, PayMethod.card, "단순 변심", "홍길동", "국민", "123"), iamportClient))
                    .isInstanceOf(RuntimeException.class);

            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETE);
            verify(productVariantRepository, never()).increaseStock(anyLong(), anyLong());
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

    // 옵션 ID가 정해진 주문 상품 (재고 복구, 장바구니 정리 테스트용)
    private OrderProduct orderProductOf(Long variantId, int quantity, BigDecimal priceAtOrder) {
        ProductVariant variant = mock(ProductVariant.class);
        lenient().when(variant.getProduct()).thenReturn(Product.ofId(1L));
        lenient().when(variant.getVariantId()).thenReturn(variantId);
        return OrderProduct.createOrderProduct(variant, "테스트 상품", "M", null, priceAtOrder, quantity);
    }

    // 옵션 ID가 정해진 장바구니 항목
    private Cart cartOf(Long variantId) {
        ProductVariant variant = mock(ProductVariant.class);
        lenient().when(variant.getVariantId()).thenReturn(variantId);
        Cart cart = mock(Cart.class);
        lenient().when(cart.getProductVariant()).thenReturn(variant);
        return cart;
    }

    /**
     * Iamport Payment Mock 생성.
     * 외부 API 응답을 Mockito로 대체하여 실제 Iamport 서버 없이 테스트.
     *
     * processPaymentDone()이 실제로 읽는 필드만 스텁함(getImpUid/getPayMethod/getBuyerAddr/getBuyerEmail).
     * getAmount()/getMerchantUid()는 프로덕션 코드에서 안 읽어서 스텁해도 항상 unused —
     * Mockito strict-stub 모드에서 UnnecessaryStubbingException 원인이라 제거함.
     */
    private Payment mockIamportPayment(String impUid, String merchantUid) {
        Payment payment = mock(Payment.class);
        given(payment.getImpUid()).willReturn(impUid);
        given(payment.getMerchantUid()).willReturn(merchantUid); // 서버가 발급한 주문번호로 결제한 정상 결제
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
