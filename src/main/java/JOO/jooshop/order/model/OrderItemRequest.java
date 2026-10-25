package JOO.jooshop.order.model;

import jakarta.validation.constraints.Max;
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
    @Max(value = MAX_QUANTITY, message = "한 번에 주문할 수 있는 수량은 최대 " + MAX_QUANTITY + "개입니다.")
    private int quantity;

    /** 한 항목당 최대 주문 수량 (비정상적으로 큰 수량으로 재고를 한꺼번에 잡는 것을 막는다) */
    public static final int MAX_QUANTITY = 99;
}
