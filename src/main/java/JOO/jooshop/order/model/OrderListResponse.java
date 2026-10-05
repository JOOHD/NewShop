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
    /** 주문내역 목록에 보여줄 대표 썸네일 — 주문 상품 중 첫 번째 상품의 주문 당시 이미지 경로 */
    private String thumbnail;

    // 주문 엔티티를 목록 응답으로 변환 (첫 상품 이미지를 썸네일로 사용)
    public static OrderListResponse from(Orders order) {
        String thumbnail = order.getOrderProducts().stream()
                .map(op -> op.getProductImg())
                .filter(img -> img != null && !img.isBlank())
                .findFirst()
                .orElse(null);

        return OrderListResponse.builder()
                .thumbnail(thumbnail)
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
