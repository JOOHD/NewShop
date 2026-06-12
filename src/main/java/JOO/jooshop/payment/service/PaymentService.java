package JOO.jooshop.payment.service;

import JOO.jooshop.global.exception.ResponseMessageConstants;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static JOO.jooshop.global.authorization.MemberAuthorizationUtil.verifyUserIdMatch;

/*
 * [PaymentService]
 * 기존 -> 결제이력 생성, Redis 조회, Cart 변환, 환불 생성, 상태 변경까지 책임 집중
 * 리팩토링 -> 서비스는 결제 흐름 제어만 담당하고,
 *            PaymentHistory / PaymentRefund 생성과 상태 변경은 엔티티 도메인 메서드에 위임
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final OrderRepository orderRepository;
    private final MemberAccountService memberAccountService;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;

    public void processPaymentDone(Payment response, PaymentRequestDto request) {
        verifyUserIdMatch(request.getMemberId());

        Orders order = getOrderById(request.getOrderId());
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

    @Transactional(readOnly = true)
    public List<PaymentHistoryDto> getPaymentHistoriesByMemberId(Long memberId) {
        verifyUserIdMatch(memberId);

        return paymentRepository.findAllByMember_Id(memberId).stream()
                .map(PaymentHistoryDto::from)
                .toList();
    }

    public IamportResponse<Payment> cancelPayment(
            Long paymentHistoryId,
            PaymentCancelDto requestDto,
            IamportClient iamportClient
    ) throws IamportResponseException, IOException {

        PaymentHistory paymentHistory = paymentRepository.findById(paymentHistoryId)
                .orElseThrow(() -> new PaymentHistoryNotFoundException(ResponseMessageConstants.PAYMENT_HISTORY_NOT_FOUND));

        if (!paymentHistory.isCancelable()) {
            throw new IllegalStateException("취소 가능한 결제 상태가 아닙니다.");
        }

        CancelData cancelData = new CancelData(
                paymentHistory.getImpUid(),
                true,
                paymentHistory.getTotalPrice()
        );

        IamportResponse<Payment> cancelResponse = iamportClient.cancelPaymentByImpUid(cancelData);

        if (cancelResponse.getCode() != 0) {
            throw new PaymentCancelFailureException("환불 실패 : " + cancelResponse.getMessage());
        }

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

    private Orders getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException(ResponseMessageConstants.ORDER_NOT_FOUND));
    }

    private void deletePaymentRedisData(Long memberId) {
        redisTemplate.delete("cartIds:" + memberId);
        redisTemplate.delete("tempOrder:" + memberId);
    }
}