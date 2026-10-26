package JOO.jooshop.order.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 금액 미리보기 응답 — 화면은 이 값을 그대로 표시한다 (화면에서 금액·배송비를 계산하지 않는다).
 *
 * @param lines       상품별 상세 (단가는 할인가 기준)
 * @param itemsTotal  상품 합계
 * @param deliveryFee 배송비
 * @param payAmount   결제 금액 = 상품 합계 + 배송비 (주문 생성 후 결제 검증의 기준값과 같은 계산)
 */
public record OrderPreviewResponse(
        List<Line> lines,
        BigDecimal itemsTotal,
        BigDecimal deliveryFee,
        BigDecimal payAmount
) {

    public record Line(
            Long cartId,
            Long variantId,
            String productName,
            String size,
            String thumbnailPath,
            int quantity,
            BigDecimal originalPrice,
            int discountRate,
            BigDecimal unitPrice,
            BigDecimal linePrice
    ) {
    }
}
