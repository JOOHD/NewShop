package JOO.jooshop.order.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 금액 미리보기 요청 — 주문을 만들지 않고 "이 항목들을 주문하면 얼마인가"만 묻는다.
 * 장바구니 주문은 cartIds, 바로 구매는 items 중 하나를 보낸다 (주문 생성 요청과 같은 규칙).
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderPreviewRequest {

    private List<Long> cartIds;

    @Valid
    @Size(max = 20, message = "한 번에 주문할 수 있는 상품 종류는 최대 20개입니다.")
    private List<OrderItemRequest> items;
}
