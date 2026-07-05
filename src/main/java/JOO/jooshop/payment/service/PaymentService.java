package JOO.jooshop.payment.service;

import JOO.jooshop.global.exception.customException.OrderNotFoundException;
import JOO.jooshop.global.exception.customException.PaymentCancelFailureException;
import JOO.jooshop.global.exception.customException.PaymentHistoryNotFoundException;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentHistory;
import JOO.jooshop.payment.entity.PaymentRefund;
import JOO.jooshop.payment.model.PaymentCancelDto;
import JOO.jooshop.payment.model.PaymentHistoryDto;
import JOO.jooshop.payment.model.PaymentRequestDto;
import JOO.jooshop.payment.repository.PaymentRefundRepository;
import JOO.jooshop.payment.repository.PaymentRepository;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.CancelData;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.verifyUserIdMatch;

/**
 * [PaymentService 트랜잭션 전략]
 *
 * 클래스 기본값: @Transactional
 *   - rollbackFor = Exception.class 제거
 *   - 이유: cancelPayment의 Checked Exception(IOException, IamportResponseException)은
 *     try-catch에서 이미 RuntimeException(PaymentCancelFailureException)으로 변환됨
 *     → 클래스 밖으로 Checked Exception이 나가지 않음 → rollbackFor 불필요
 *   - processPaymentDone도 Checked Exception을 직접 던지지 않음
 *
 * 조회 메서드: @Transactional(readOnly = true)로 override
 *   - 클래스 기본 @Transactional을 통째로 교체
 *   - Dirty Checking 비활성화 → 성능 최적화
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderRepository orderRepository;
    private final MemberAccountService memberAccountService;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;

    /**
     * 결제 완료 처리
     * Order 상태 변경 + PaymentHistory 저장 + Redis 정리가 하나의 트랜잭션
     * 중간 실패 시 전부 롤백
     */
    public void processPaymentDone(Payment response, PaymentRequestDto request) {
        verifyUserIdMatch(request.getMemberId());

        Orders order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException("주문 정보를 찾을 수 없습니다."));

        Member member = memberAccountService.findMemberById(request.getMemberId());

        order.changePaymentStatus(JOO.jooshop.payment.entity.PaymentStatus.COMPLETE);

        List<OrderProduct> orderProducts = order.getOrderProducts();

        for (OrderProduct orderProduct : orderProducts) {
            PaymentHistory paymentHistory = PaymentHistory.createPaymentHistory(
                    member,
                    order,
                    orderProduct,
                    response.getImpUid(),
                    response.getPayMethod(),
                    order.getTotalPrice(),
                    response.getBankCode(),
                    response.getBankName(),
                    response.getBuyerAddr(),
                    response.getBuyerEmail()
            );
            paymentRepository.save(paymentHistory);
        }

        deletePaymentRedisData(member.getId());
    }

    /**
     * 결제 이력 조회
     *
     * 클래스 기본 @Transactional을 readOnly=true로 override
     * → "이 메서드는 읽기 전용" 명시 + Dirty Checking 비활성화
     */
    @Transactional(readOnly = true)
    public List<PaymentHistoryDto> getPaymentHistoriesByMemberId(Long memberId) {
        verifyUserIdMatch(memberId);

        return paymentRepository.findAllByMember_Id(memberId).stream()
                .map(PaymentHistoryDto::from)
                .toList();
    }

    /**
     * 결제 취소
     *
     * Iamport Checked Exception(IOException, IamportResponseException)을
     * try-catch로 잡아 RuntimeException(PaymentCancelFailureException)으로 변환
     * → 클래스 밖으로 Checked Exception이 나가지 않음
     * → rollbackFor = Exception.class 없어도 RuntimeException이 트랜잭션 롤백 트리거
     *
     * 순서 중요:
     *   Iamport API 호출 성공 확인 → 그 다음에 DB 변경(markCanceled())
     *   API 실패 시점엔 DB 변경이 없으므로 롤백해도 안전
     */
    public IamportResponse<Payment> cancelPayment(
            Long paymentHistoryId,
            PaymentCancelDto requestDto,
            IamportClient iamportClient
    ) {
        PaymentHistory paymentHistory = paymentRepository.findById(paymentHistoryId)
                .orElseThrow(() -> new PaymentHistoryNotFoundException("결제 내역을 찾을 수 없습니다."));

        if (!paymentHistory.isCancelable()) {
            throw new IllegalStateException("이미 취소되었거나 취소할 수 없는 결제입니다.");
        }

        CancelData cancelData = new CancelData(
                paymentHistory.getImpUid(),
                true,
                paymentHistory.getTotalPrice()
        );

        // Checked Exception → RuntimeException 변환
        // 이 시점 DB 변경 없음 → 예외 발생 시 롤백해도 안전
        IamportResponse<Payment> cancelResponse;
        try {
            cancelResponse = iamportClient.cancelPaymentByImpUid(cancelData);
        } catch (IamportResponseException | IOException e) {
            log.error("Iamport 환불 API 실패 — impUid: {}, 원인: {}", paymentHistory.getImpUid(), e.getMessage());
            throw new PaymentCancelFailureException("환불 처리 중 오류가 발생했습니다: " + e.getMessage());
        }

        if (cancelResponse.getCode() != 0) {
            throw new PaymentCancelFailureException("환불이 거부되었습니다: " + cancelResponse.getMessage());
        }

        // API 성공 확인 후에만 DB 변경 — 순서 핵심
        paymentHistory.markCanceled();

        PaymentRefund refund = PaymentRefund.createRefund(
                paymentHistory,
                requestDto.getReason(),
                null,
                requestDto.getRefundHolder(),
                requestDto.getRefundBank(),
                requestDto.getRefundAccount()
        );
        paymentRefundRepository.save(refund);

        return cancelResponse;
    }

    private void deletePaymentRedisData(Long memberId) {
        redisTemplate.delete("cartIds:" + memberId);
        redisTemplate.delete("tempOrder:" + memberId);
    }
}
