package JOO.jooshop.order.entity;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.payment.entity.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 상태 전이 규칙 테스트.
 *
 *   PENDING  → COMPLETE, CANCELED
 *   COMPLETE → CANCELED
 *   CANCELED → (없음)
 */
class OrderStatusTransitionTest {

    @Test
    @DisplayName("결제 대기 주문은 결제 완료로 바꿀 수 있다")
    void pending_toComplete_allowed() {
        Orders order = newOrder();

        order.markPaid();

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETE);
    }

    @Test
    @DisplayName("결제 대기 주문은 취소할 수 있다 (만료)")
    void pending_toCanceled_allowed() {
        Orders order = newOrder();

        order.markCanceled();

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("결제 완료 주문은 취소할 수 있다 (환불)")
    void complete_toCanceled_allowed() {
        Orders order = newOrder();
        order.markPaid();

        order.markCanceled();

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    @DisplayName("취소된 주문은 다시 결제 완료로 되돌릴 수 없다")
    void canceled_toComplete_rejected() {
        Orders order = newOrder();
        order.markCanceled();

        assertThatThrownBy(order::markPaid)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANCELED");
    }

    @Test
    @DisplayName("결제 완료 주문을 결제 대기로 되돌릴 수 없다")
    void complete_toPending_rejected() {
        Orders order = newOrder();
        order.markPaid();

        assertThatThrownBy(() -> order.changePaymentStatus(PaymentStatus.PENDING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("같은 상태로의 변경(결제 완료 → 결제 완료)도 허용하지 않는다")
    void sameStatus_rejected() {
        Orders order = newOrder();
        order.markPaid();

        assertThatThrownBy(order::markPaid).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("PaymentStatus의 전이표가 규칙과 일치한다")
    void transitionTable() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.COMPLETE)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.CANCELED)).isTrue();
        assertThat(PaymentStatus.COMPLETE.canTransitionTo(PaymentStatus.CANCELED)).isTrue();

        assertThat(PaymentStatus.COMPLETE.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.CANCELED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.CANCELED.canTransitionTo(PaymentStatus.COMPLETE)).isFalse();
        assertThat(PaymentStatus.CANCELED.canTransitionTo(PaymentStatus.CANCELED)).isFalse();
    }

    private Orders newOrder() {
        Member member = Member.registerGeneral(
                "test@example.com", "encodedPw", "홍길동", "길동이", "010-1234-5678", "uuid");
        return Orders.createOrder(member, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구", null, PayMethod.card, "order_test");
    }
}
