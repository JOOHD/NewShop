package JOO.jooshop.productVariant.model;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import JOO.jooshop.product.entity.enums.ProductType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductVariantDto {

    // Product 엔티티 필드
    private ProductType productType;
    private String productName;
    private String manufacturer;
    private Boolean isDiscount;
    private Boolean isRecommend;

    // ProductVariant 필드
    private Long inventoryId;
    private Long productId;
    private Long colorId;
    private String color;
    private Long categoryId;
    private String category;
    private Gender gender;
    private Size size;
    private Long initialStock;
    private Long additionalStock;
    private Long productStock;
    private boolean isSoldOut;
    private boolean isRestockAvailable;
    private boolean isRestocked;

    /** Entity → DTO (Static Factory) */
    public static ProductVariantDto toDto(ProductVariant pm) {
        return ProductVariantDto.builder()
                .productType(pm.getProduct().getProductType())
                .productName(pm.getProduct().getProductName())
                .manufacturer(pm.getProduct().getManufacturer())
                .isDiscount(pm.getProduct().isDiscount())
                .isRecommend(pm.getProduct().isRecommend())
                .inventoryId(pm.getInventoryId())
                .productId(pm.getProduct().getProductId())
                .colorId(pm.getColor().getColorId())
                .color(pm.getColor().getColor())
                .categoryId(pm.getCategory().getCategoryId())
                .category(pm.getCategory().getName())
                .gender(pm.getGender())
                .size(pm.getSize())
                .initialStock(pm.getInitialStock())
                .additionalStock(pm.getAdditionalStock())
                .productStock(pm.getProductStock())
                .isSoldOut(pm.isSoldOut())
                .isRestockAvailable(pm.isRestockAvailable())
                .isRestocked(pm.isRestocked())
                .build();
    }

    /** DTO → Entity (Static Factory) */
    public static ProductVariant toEntity(ProductVariantDto dto) {
        // static factory로 각각 객체 생성
        Product product = Product.ofId(dto.getProductId());
        ProductColor color = ProductColor.ofId(dto.getColorId());
        Category category = Category.ofId(dto.getCategoryId());

        // ProductVariant 생성
        return ProductVariant.of(
                color,
                category,
                dto.getGender(),
                dto.getSize(),
                dto.getInitialStock(),
                dto.isRestockAvailable(),
                dto.isRestocked(),
                dto.isSoldOut()
        );
    }

}
