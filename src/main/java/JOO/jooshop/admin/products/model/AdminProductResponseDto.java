package JOO.jooshop.admin.products.model;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminProductResponseDto(
        Long productId,
        String productName,
        ProductType productType,
        BigDecimal price,
        Integer discountRate,
        String productInfo,
        String thumbnailUrl,
        LocalDateTime createdAt
) {
    public static AdminProductResponseDto from(Product product, String thumbnailUrl) {
        return new AdminProductResponseDto(
                product.getProductId(),
                product.getProductName(),
                product.getProductType(),
                product.getPrice(),
                product.getDiscountRate(),
                product.getProductInfo(),
                thumbnailUrl,
                product.getCreatedAt()
        );
    }
}