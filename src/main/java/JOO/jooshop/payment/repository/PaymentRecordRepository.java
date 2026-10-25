package JOO.jooshop.payment.repository;

import JOO.jooshop.payment.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    // 이미 기록된 결제번호인지 확인 (멱등 처리의 빠른 경로. 최종 방어선은 imp_uid UNIQUE 제약)
    boolean existsByImpUid(String impUid);

    Optional<PaymentRecord> findByImpUid(String impUid);
}
