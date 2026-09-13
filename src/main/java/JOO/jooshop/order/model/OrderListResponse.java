package JOO.jooshop.order.model;

import JOO.jooshop.order.entity.Orders;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 회원 본인의 "주문내역" 목록 조회용 응답 DTO.
 * admin.orders.model.AdminOrderListResponse와 필드가 같지만,
 * 관리자 패키지에 회원용 API가 의존하지 않도록 별도로 둔다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderListResponse {

    private Long orderId;
    private String ordererName;
    private String phoneNumber;
    private BigDecimal totalPrice;
    private String status;
    private LocalDateTime orderDate;
    private String productSummary;

    public static OrderListResponse from(Orders order) {
        return OrderListResponse.builder()
                .orderId(order.getOrderId())
                .ordererName(order.getOrdererName())
                .phoneNumber(order.getPhoneNumber())
                .totalPrice(order.getTotalPrice())
                .status(order.getPaymentStatus().name())
                .orderDate(order.getOrderDay())
                .productSummary(order.getProductNameSummary())
                .build();
    }
}
