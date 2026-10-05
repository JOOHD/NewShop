package JOO.jooshop.productVariant.model;

import JOO.jooshop.productVariant.entity.ProductVariant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class ProductVariantUpdateDto {

    // 색상/사이즈/초기 재고는 옵션을 특정 짓는 값이라 수정 대상에서 제외 — 바꾸려면 옵션을 새로 생성해야 함
    private Long categoryId;
    private Long additionalStock;
    private Long productStock;
    private Boolean isRestockAvailable = false;
    private Boolean isRestocked = false;
    private Boolean isSoldOut = false;

    // 옵션 엔티티를 수정용 DTO로 변환
    public ProductVariantUpdateDto(ProductVariant productVariant) {
        this(
                productVariant.getCategory().getCategoryId(),
                productVariant.getAdditionalStock(),
                productVariant.getProductStock(),
                productVariant.isRestockAvailable(),
                productVariant.isRestocked(),
                productVariant.isSoldOut()
        );
    }
}
