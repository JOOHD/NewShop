package JOO.jooshop.payment.model;

import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.payment.entity.PaymentRefund;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/*
 * [PaymentCancelDto]
 * 기존 -> 환불 요청과 응답 정보 역할이 다소 섞여 있음
 * 리팩토링 -> 환불 요청 전용 DTO 로 사용하고,
 *            PaymentRefund -> DTO 변환은 from() 정적 메서드로 명확히 표현
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCancelDto {

    private Long paymentHistoryId;
    private PayMethod payMethod;
    private String reason;
    private String refundHolder;
    private String refundBank;
    private String refundAccount;

    // 환불 엔티티를 취소 응답 DTO로 변환
    public static PaymentCancelDto from(PaymentRefund paymentRefund) {
        return new PaymentCancelDto(
                paymentRefund.getPaymentHistory().getId(),
                paymentRefund.getPaymentHistory().getOrders().getPayMethod(),
                paymentRefund.getReason(),
                paymentRefund.getRefundHolder(),
                paymentRefund.getRefundBank(),
                paymentRefund.getRefundAccount()
        );
    }
}