package JOO.jooshop.payment.entity;

/*
 * [PaymentStatus]
 * 기존 -> paymentStatus / statusType 두 필드가 혼재하며 의미가 중복됨
 * 리팩토링 -> 결제 상태를 하나의 Enum 으로 통일하여 결제/취소 상태를 단일 기준으로 관리
 */
public enum PaymentStatus {

    PENDING("대기"),
    COMPLETE("완료"),
    CANCELED("취소");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }

    // 결제 상태 설명 문구
    public String getDescription() {
        return description;
    }

    // 취소 가능 여부 — 결제 완료 상태일 때만
    public boolean isCancelable() {
        return this == COMPLETE;
    }

    /**
     * 허용된 상태 전이인지 확인한다.
     *
     *   PENDING  → COMPLETE  (결제 검증 성공)
     *   PENDING  → CANCELED  (결제 안 하고 만료/취소)
     *   COMPLETE → CANCELED  (결제 후 취소·환불)
     *
     * 그 외(CANCELED에서 되살리기, COMPLETE에서 PENDING으로 되돌리기 등)는 허용하지 않는다.
     */
    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == COMPLETE || next == CANCELED;
            case COMPLETE -> next == CANCELED;
            case CANCELED -> false;
        };
    }
}