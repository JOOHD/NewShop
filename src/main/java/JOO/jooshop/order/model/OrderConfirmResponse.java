package JOO.jooshop.order.model;

import JOO.jooshop.order.entity.Orders;

import java.math.BigDecimal;

/**
 * 주문 확정(결제 대기 주문 생성) 응답.
 * 화면은 이 값으로만 결제창을 띄운다 — 주문번호(merchantUid)와 결제 금액(amount)을 서버가 확정해서 내려준다.
 *
 * @param orderId     주문 ID (결제 검증 요청에 다시 보냄)
 * @param merchantUid 서버가 발급한 주문번호 (Iamport merchant_uid로 그대로 사용)
 * @param amount      결제해야 하는 금액 = 상품 합계 + 배송비 (서버 계산)
 * @param orderName   결제창에 표시할 주문명
 */
public record OrderConfirmResponse(
        Long orderId,
        String merchantUid,
        BigDecimal amount,
        String orderName
) {
    public static OrderConfirmResponse from(Orders order) {
        return new OrderConfirmResponse(
                order.getOrderId(),
                order.getMerchantUid(),
                order.getPayAmount(),
                order.getProductNameSummary()
        );
    }
}
