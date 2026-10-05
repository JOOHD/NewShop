package JOO.jooshop.payment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/*
 * [PaymentRefund]
 * 기존 -> 생성자가 여러 개 존재하여 어떤 생성 경로가 공식 경로인지 불명확
 * 리팩토링 -> createRefund 정적 팩토리 메서드 하나로 생성 경로를 통일하고,
 *            PaymentHistory 에 종속된 환불 엔티티로 정리
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payment_refund")
public class PaymentRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_refund_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_history", nullable = false)
    private PaymentHistory paymentHistory;

    @Column(name = "imp_uid", nullable = false)
    private String impUid;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "refund_tel")
    private String refundTel;

    @Column(name = "checksum")
    private Integer checksum;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "refund_holder")
    private String refundHolder;

    @Column(name = "refund_bank")
    private String refundBank;

    @Column(name = "refund_account")
    private String refundAccount;

    @Column(name = "refund_at", nullable = false)
    private LocalDateTime refundAt;

    // 환불 요청 정보(사유, 연락처, 환불 계좌) 생성
    public static PaymentRefund createRefund(
            PaymentHistory paymentHistory,
            String reason,
            String refundTel,
            String refundHolder,
            String refundBank,
            String refundAccount
    ) {
        if (paymentHistory == null) {
            throw new IllegalArgumentException("결제 이력은 필수입니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("환불 사유는 필수입니다.");
        }

        PaymentRefund refund = new PaymentRefund();
        refund.paymentHistory = paymentHistory;
        refund.impUid = paymentHistory.getImpUid();
        refund.amount = paymentHistory.getTotalPrice().intValue();
        refund.checksum = paymentHistory.getTotalPrice().intValue();
        refund.reason = reason;
        refund.refundTel = refundTel;
        refund.refundHolder = refundHolder;
        refund.refundBank = refundBank;
        refund.refundAccount = refundAccount;
        refund.refundAt = LocalDateTime.now();
        return refund;
    }
}