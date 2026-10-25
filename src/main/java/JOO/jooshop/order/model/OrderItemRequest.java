package JOO.jooshop.order.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "바로 구매" 주문 항목 — 장바구니를 거치지 않고 옵션(variantId)과 수량만 받는다.
 * 가격·상품명은 서버가 DB에서 읽는다 (클라이언트 값은 받지 않는다).
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemRequest {

    @NotNull(message = "상품 옵션은 필수입니다.")
    private Long variantId;

    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    private int quantity;
}
