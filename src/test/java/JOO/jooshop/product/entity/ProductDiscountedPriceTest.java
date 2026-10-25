package JOO.jooshop.product.entity;

import JOO.jooshop.product.entity.enums.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 할인가 계산 테스트.
 * 장바구니 화면, 주문 저장 금액, 결제 금액이 같은 단가를 쓰도록 Product가 단일 계산을 제공한다.
 */
class ProductDiscountedPriceTest {

    @Test
    @DisplayName("25% 할인이면 159,500원 상품의 판매가는 119,625원이다 (실제 화면에서 확인된 값)")
    void discounted_25percent() {
        Product product = product(BigDecimal.valueOf(159_500), true, 25);

        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("119625");
    }

    @Test
    @DisplayName("할인 상품이 아니면 정가 그대로다")
    void notDiscounted_returnsOriginalPrice() {
        Product product = product(BigDecimal.valueOf(159_500), false, 25);

        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("159500");
    }

    @Test
    @DisplayName("원 단위 미만은 절사한다")
    void discounted_roundsDownToWon() {
        // 10,001 * 0.85 = 8,500.85 → 8,500
        Product product = product(BigDecimal.valueOf(10_001), true, 15);

        assertThat(product.getDiscountedPrice()).isEqualByComparingTo("8500");
    }

    private Product product(BigDecimal price, boolean discount, int rate) {
        return Product.create("테스트 상품", ProductType.valueOf(firstType()), price,
                "설명", "제조사", discount, rate, false);
    }

    private String firstType() {
        return ProductType.values()[0].name();
    }
}
