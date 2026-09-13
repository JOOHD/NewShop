package JOO.jooshop.order.repository;

import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Orders,Long> {
    Optional<Orders> findByMemberId(Long memberId);

    List<Orders> findAllByMemberIdOrderByOrderDayDesc(Long memberId);

}
