package JOO.jooshop.payment.entity;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.order.entity.Orders;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [PaymentRecord — 결제 1건 = 1행]
 *
 * PaymentHistory는 "주문 상품마다 1행"이라 같은 결제번호(imp_uid)가 여러 행에 들어가고, 그래서 imp_uid에 UNIQUE를 걸 수 없었다.
 * 그 결과 중복 결제 방어를 조회(existsByImpUid) + 주문 행 잠금으로만 했다.
 *
 * PaymentRecord는 결제 한 건을 한 행으로 기록하고 imp_uid에 UNIQUE 제약을 건다.
 *   - 같은 결제가 두 번 저장되는 것을 DB가 마지막 방어선으로 막는다 (코드의 조회·잠금이 뚫려도 안전)
 *   - 결제 취소 시 "이 결제 전체"를 한 곳에서 상태 변경할 수 있다 (상품 행마다 따로 취소하던 문제 해소)
 *
 * PaymentHistory는 상품별 구매 내역 조회·리뷰용으로 그대로 유지한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_record_imp_uid", columnNames = "imp_uid")
)
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_record_id")
    private Long id;

    @Column(name = "imp_uid", nullable = false)
    private String impUid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orders_id", nullable = false)
    private Orders orders;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "pay_method", nullable = false)
    private String payMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // 결제 완료 기록 생성
    public static PaymentRecord paid(Member member, Orders orders, String impUid, String payMethod, BigDecimal amount) {
        if (member == null || orders == null) {
            throw new IllegalArgumentException("회원과 주문은 필수입니다.");
        }
        if (impUid == null || impUid.isBlank()) {
            throw new IllegalArgumentException("impUid는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }

        PaymentRecord record = new PaymentRecord();
        record.member = member;
        record.orders = orders;
        record.impUid = impUid;
        record.payMethod = (payMethod == null || payMethod.isBlank()) ? "unknown" : payMethod;
        record.amount = amount;
        record.status = PaymentStatus.COMPLETE;
        record.paidAt = LocalDateTime.now();
        return record;
    }

    // 결제 취소 — 허용된 전이(COMPLETE → CANCELED)만 가능
    public void markCanceled() {
        if (!this.status.canTransitionTo(PaymentStatus.CANCELED)) {
            throw new IllegalStateException("이미 취소되었거나 취소할 수 없는 결제입니다.");
        }
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }
}
