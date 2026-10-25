package JOO.jooshop.payment.repository;

import JOO.jooshop.payment.entity.PaymentHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/*
 * [PaymentRepository]
 * 기존 -> findByMemberId / findAllByMemberId 중복, 단순 조회 위주
 * 리팩토링 -> aggregate 조회 기준으로 메서드 명 정리,
 *            결제내역 조회 시 필요한 연관 데이터를 함께 조회하도록 EntityGraph 적용
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentHistory, Long> {

    @EntityGraph(attributePaths = {"member", "orders", "product", "product.productThumbnails"})
    List<PaymentHistory> findAllByMember_Id(Long memberId);

    Optional<PaymentHistory> findByImpUid(String impUid);

    // 같은 결제(imp_uid)에 속한 주문 상품별 이력 전부 — 결제 취소 시 함께 취소 처리
    List<PaymentHistory> findAllByImpUid(String impUid);

    // 같은 imp_uid가 이미 처리됐는지 확인 (결제 검증 요청의 중복 처리 방지, 멱등성)
    boolean existsByImpUid(String impUid);
}