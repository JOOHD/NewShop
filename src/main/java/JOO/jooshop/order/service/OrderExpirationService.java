package JOO.jooshop.order.service;

import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentStatus;
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

/**
 * 결제되지 않은 주문 1건을 취소하고 재고를 되돌린다.
 *
 * 스케줄러(OrderExpirationScheduler)와 클래스를 분리한 이유:
 * 같은 클래스 안에서 this.expire()로 호출하면 프록시를 거치지 않아 @Transactional이 적용되지 않는다(self-invocation).
 * 주문 1건씩 별도 트랜잭션으로 처리해야 한 건이 실패해도 다른 주문의 처리가 롤백되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;

    /**
     * @return 이번 호출로 실제 취소했으면 true, 이미 결제됐거나 없는 주문이면 false
     */
    public boolean expire(Long orderId) {
        // 결제 검증(processPaymentDone)과 같은 잠금을 사용 — 만료 처리와 결제 완료가 동시에 와도 한쪽씩만 처리된다.
        Orders order = orderRepository.findByIdForUpdate(orderId).orElse(null);

        // 잠금을 잡은 뒤 상태를 다시 확인한다 (목록 조회 이후 결제가 완료됐을 수 있음)
        if (order == null || order.getPaymentStatus() != PaymentStatus.PENDING) {
            return false;
        }

        order.markCanceled();

        // 같은 옵션은 수량을 합산하고, 옵션 ID 오름차순으로 복구 — 재고 차감과 같은 순서라 데드락을 피한다
        Map<Long, Long> qtyByVariant = new TreeMap<>();
        for (OrderProduct orderProduct : order.getOrderProducts()) {
            qtyByVariant.merge(orderProduct.getProductVariant().getVariantId(),
                    (long) orderProduct.getQuantity(), Long::sum);
        }
        qtyByVariant.forEach(productVariantRepository::increaseStock);

        log.info("결제 대기 주문 만료 처리 — orderId: {}, 복구 옵션 수: {}", orderId, qtyByVariant.size());
        return true;
    }
}
