package JOO.jooshop.order.service;

import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일정 시간이 지나도 결제되지 않은(PENDING) 주문을 주기적으로 취소하고 재고를 되돌린다.
 *
 * 결제 전에 주문을 먼저 만들면서 재고를 차감하므로, 결제창을 닫거나 이탈한 주문이 재고를 계속 잡고 있게 된다.
 * 이 배치가 그 재고를 풀어 준다.
 *
 * 설정(application.yml / 환경변수로 변경 가능):
 *   order.pending-expire.minutes            만료 기준 시간(분), 기본 30
 *   order.pending-expire.check-interval-ms  점검 주기(ms), 기본 300000(5분)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderExpirationService orderExpirationService;

    @Value("${order.pending-expire.minutes:30}")
    private long expireMinutes;

    @Scheduled(fixedDelayString = "${order.pending-expire.check-interval-ms:300000}")
    public void expirePendingOrders() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(expireMinutes);
        List<Long> orderIds = orderRepository.findOrderIdsByStatusAndCreatedBefore(PaymentStatus.PENDING, before);

        int expired = 0;
        for (Long orderId : orderIds) {
            try {
                if (orderExpirationService.expire(orderId)) {
                    expired++;
                }
            } catch (Exception e) {
                // 한 주문의 실패가 나머지 주문 처리를 막지 않도록 로그만 남기고 계속한다
                log.error("결제 대기 주문 만료 처리 실패 — orderId: {}", orderId, e);
            }
        }

        if (expired > 0) {
            log.info("결제 대기 주문 {}건을 만료 처리했습니다 (기준: {}분 경과)", expired, expireMinutes);
        }
    }
}
