package JOO.jooshop.productVariant.model;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class InventoryCreateDto {

    @NotNull(message = "상품은 필수로 지정해야 합니다.")
    private Long productId;

    @NotNull(message = "색상은 필수로 지정해야 합니다.")
    private Long colorId;

    @NotNull(message = "카테고리는 필수로 지정해야 합니다.")
    private Long categoryId;

    @NotNull(message = "성별은 필수로 지정해야 합니다.")
    private Gender gender;

    @NotNull(message = "사이즈는 필수로 지정해야 합니다.")
    private Size size;

    @NotNull(message = "초기 재고는 필수입니다.")
    private Long initialStock;

    private Long additionalStock = 0L; // 생성할 땐 무조건 0
    private Boolean isRestockAvailable = false;
    private Boolean isRestocked = false;
    private Boolean isSoldOut = false;

    public InventoryCreateDto(ProductVariant pm) {
        this(
                pm.getProduct().getProductId(),
                pm.getColor().getColorId(),
                pm.getCategory().getCategoryId(),
                pm.getGender(),
                pm.getSize(),
                pm.getInitialStock(),
                pm.getAdditionalStock(),
                pm.isRestockAvailable(),
                pm.isRestocked(),
                pm.isSoldOut()
        );
    }

    public ProductVariant toEntity(Product product, ProductColor color, Category category) {
        // ProductVariant.of 시그니처에 맞춰 정확히 호출
        return ProductVariant.of(
                color,
                category,
                this.gender,
                this.size,
                this.initialStock,        // long
                this.isRestockAvailable,
                this.isRestocked,
                this.isSoldOut
        );
    }
}
