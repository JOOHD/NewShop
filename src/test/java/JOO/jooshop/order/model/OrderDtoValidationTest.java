package JOO.jooshop.order.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 바로 구매(items) 요청의 입력 검증 테스트 — 수량·항목 개수 상한.
 */
class OrderDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("정상 수량(1~99)의 바로 구매 요청은 통과한다")
    void validQuantity_passes() {
        OrderDto dto = dto(List.of(item(7L, 2)));

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    @DisplayName("수량이 상한(99)을 넘으면 거부된다")
    void quantityOverMax_rejected() {
        OrderDto dto = dto(List.of(item(7L, 100)));

        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("수량이 0 이하이면 거부된다")
    void quantityZero_rejected() {
        OrderDto dto = dto(List.of(item(7L, 0)));

        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    @DisplayName("상품 종류가 20개를 넘으면 거부된다")
    void tooManyItems_rejected() {
        List<OrderItemRequest> items = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> item((long) i, 1))
                .toList();

        assertThat(validator.validate(dto(items))).isNotEmpty();
    }

    private OrderDto dto(List<OrderItemRequest> items) {
        return OrderDto.builder()
                .memberId(1L)
                .items(items)
                .postCode("12345")
                .address("서울시 강남구")
                .username("홍길동")
                .build();
    }

    private OrderItemRequest item(Long variantId, int quantity) {
        return OrderItemRequest.builder().variantId(variantId).quantity(quantity).build();
    }
}
