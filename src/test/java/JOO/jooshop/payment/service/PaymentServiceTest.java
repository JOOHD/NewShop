package JOO.jooshop.payment.service;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.entity.PaymentStatus;
import JOO.jooshop.payment.model.PaymentRequestDto;
import JOO.jooshop.payment.repository.PaymentRefundRepository;
import JOO.jooshop.payment.repository.PaymentRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * PaymentService 단위 테스트.
 *
 * 핵심 검증 포인트:
 * 1. 결제 완료 처리 — PaymentHistory 저장, Redis 삭제
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
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private IamportClient iamportClient;

    @InjectMocks
    private PaymentService paymentService;

    // ========================================================
    // 결제 완료 처리
    // ========================================================
    @Nested
    @DisplayName("결제 완료 처리")
    class ProcessPayment {

        @Test
        @DisplayName("정상 결제 완료 시 PaymentHistory가 저장되고 Redis 데이터가 삭제된다")
        void processPaymentDone_success() {
            // given
            Long memberId = 1L;
            Long orderId = 10L;

            Member member = createMember();
            Orders order = createOrder(member, BigDecimal.valueOf(50000));

            Payment iamportPayment = mockIamportPayment("imp_test_uid", BigDecimal.valueOf(50000));
            PaymentRequestDto request = new PaymentRequestDto(memberId, orderId, BigDecimal.valueOf(50000), List.of(1L), "imp_test_uid");

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(memberAccountService.findMemberById(memberId)).willReturn(member);

            // when
            paymentService.processPaymentDone(iamportPayment, request);

            // then
            verify(paymentRepository, atLeastOnce()).save(any(PaymentHistory.class));
            // Redis 임시 데이터 삭제 검증
            verify(redisTemplate, times(1)).delete("cartIds:" + memberId);
            verify(redisTemplate, times(1)).delete("tempOrder:" + memberId);
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 결제 시 예외 발생")
        void processPaymentDone_orderNotFound_throwsException() {
            // given
            Payment iamportPayment = mockIamportPayment("imp_uid", BigDecimal.valueOf(50000));
            PaymentRequestDto request = new PaymentRequestDto(1L, 999L, BigDecimal.valueOf(50000), List.of(), "imp_uid");

            given(orderRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> paymentService.processPaymentDone(iamportPayment, request))
                    .isInstanceOf(NoSuchElementException.class);

            // 저장이 호출되지 않음을 검증
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
            // given
            PaymentHistory history = createCanceledPaymentHistory();
            given(paymentRepository.findById(1L)).willReturn(Optional.of(history));

            // when & then
            assertThatThrownBy(() ->
                    paymentService.cancelPayment(1L, null, iamportClient)
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("취소 가능한 결제 상태가 아닙니다");

            // Iamport API가 호출되지 않음을 검증 (외부 API 불필요 호출 방지)
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
            Member member = createMember();
            Orders order = createOrder(member, BigDecimal.valueOf(30000));

            PaymentHistory h1 = PaymentHistory.createPaymentHistory(
                    member, order, null, "imp_001", "card",
                    BigDecimal.valueOf(30000), null, null, "서울", "test@test.com"
            );
            PaymentHistory h2 = PaymentHistory.createPaymentHistory(
                    member, order, null, "imp_002", "card",
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
        return Member.registerGeneral(
                "test@example.com", "encodedPw",
                "홍길동", "길동이", "010-1234-5678", "uuid"
        );
    }

    private Orders createOrder(Member member, BigDecimal totalPrice) {
        return Orders.createOrder(member, totalPrice, "서울시 강남구");
    }

    /**
     * Iamport Payment Mock 생성.
     * 외부 API 응답을 Mockito로 대체하여 실제 Iamport 서버 없이 테스트.
     */
    private Payment mockIamportPayment(String impUid, BigDecimal amount) {
        Payment payment = mock(Payment.class);
        given(payment.getImpUid()).willReturn(impUid);
        given(payment.getAmount()).willReturn(amount);
        given(payment.getPayMethod()).willReturn("card");
        given(payment.getMerchantUid()).willReturn("order_test_001");
        given(payment.getBuyerAddr()).willReturn("서울시 강남구");
        given(payment.getBuyerEmail()).willReturn("test@example.com");
        return payment;
    }

    private PaymentHistory createCanceledPaymentHistory() {
        Member member = createMember();
        Orders order = createOrder(member, BigDecimal.valueOf(30000));
        PaymentHistory history = PaymentHistory.createPaymentHistory(
                member, order, null, "imp_canceled", "card",
                BigDecimal.valueOf(30000), null, null, "서울", "test@test.com"
        );
        history.markCanceled(); // 이미 취소 상태로 설정
        return history;
    }
}
