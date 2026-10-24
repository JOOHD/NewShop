package JOO.jooshop.order.repository;

import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.payment.entity.PaymentStatus;
import JOO.jooshop.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Orders,Long> {
    Optional<Orders> findByMemberId(Long memberId);

    List<Orders> findAllByMemberIdOrderByOrderDayDesc(Long memberId);

    // 지정한 결제 상태이면서 기준 시각 이전에 만들어진 주문의 ID 목록 (결제 대기 주문 만료 배치용)
    @Query("select o.orderId from Orders o where o.paymentStatus = :status and o.orderDay < :before")
    List<Long> findOrderIdsByStatusAndCreatedBefore(@Param("status") PaymentStatus status,
                                                    @Param("before") LocalDateTime before);

    // 주문 행에 쓰기 잠금(SELECT ... FOR UPDATE)을 걸고 조회한다.
    // 같은 주문의 결제 검증 요청이 동시에 들어와도 한 번에 하나씩만 처리되게 해서,
    // "이미 처리됐는지 조회 → 저장" 사이의 틈에 중복 저장이 끼어드는 것을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Orders o where o.orderId = :orderId")
    Optional<Orders> findByIdForUpdate(@Param("orderId") Long orderId);

}
