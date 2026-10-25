package JOO.jooshop.payment.repository;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * imp_uid UNIQUE 제약 테스트.
 * 코드의 중복 확인(조회·행 잠금)이 모두 뚫려도, 같은 결제번호가 두 번 저장되는 것을 DB가 마지막으로 막는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase
class PaymentRecordUniqueTest {

    @Autowired PaymentRecordRepository paymentRecordRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired MemberRepository memberRepository;

    @Test
    @DisplayName("같은 imp_uid의 결제 기록을 두 번 저장하면 DB가 거부한다")
    void duplicateImpUid_isRejectedByDatabase() {
        Member member = memberRepository.save(Member.registerGeneral(
                "unique@example.com", "encodedPw", "홍길동", "유니크닉", "010-1234-5678", "social-unique"));
        Orders order = orderRepository.save(Orders.createOrder(member, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구", null, PayMethod.card, "order_unique"));

        paymentRecordRepository.saveAndFlush(
                PaymentRecord.paid(member, order, "imp_same", "card", BigDecimal.valueOf(10_000)));

        assertThatThrownBy(() -> paymentRecordRepository.saveAndFlush(
                PaymentRecord.paid(member, order, "imp_same", "card", BigDecimal.valueOf(10_000))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("서로 다른 imp_uid는 각각 저장된다")
    void differentImpUid_isSaved() {
        Member member = memberRepository.save(Member.registerGeneral(
                "unique2@example.com", "encodedPw", "홍길동", "유니크닉2", "010-1234-5678", "social-unique2"));
        Orders order = orderRepository.save(Orders.createOrder(member, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구", null, PayMethod.card, "order_unique2"));

        paymentRecordRepository.saveAndFlush(
                PaymentRecord.paid(member, order, "imp_a", "card", BigDecimal.valueOf(10_000)));
        paymentRecordRepository.saveAndFlush(
                PaymentRecord.paid(member, order, "imp_b", "card", BigDecimal.valueOf(10_000)));

        assertThat(paymentRecordRepository.existsByImpUid("imp_a")).isTrue();
        assertThat(paymentRecordRepository.existsByImpUid("imp_b")).isTrue();
    }
}
