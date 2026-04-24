package JOO.jooshop.product.model;

import JOO.jooshop.global.validation.ValidDiscountRate;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.product.entity.enums.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ValidDiscountRate
public class ProductRequestDto {

    @NotBlank(message = "상품 이름은 필수입니다.")
    private String productName;

    @NotNull(message = "상품 타입은 필수입니다.")
    private ProductType productType;

    @NotNull(message = "가격은 필수입니다.")
    @DecimalMin(value = "0", inclusive = true, message = "가격은 0 이상이어야 합니다.")
    private BigDecimal price;

    private String productInfo;
    private String manufacturer;

    @Builder.Default
    private Boolean isDiscount = false;

    @Builder.Default
    @Max(value = 100, message = "할인율은 100을 초과할 수 없습니다.")
    private Integer discountRate = null;

    @Builder.Default
    private Boolean isRecommend = false;

    @Valid
    private List<ProductOptionDto> options;

    /**
     * 요청 값 정리
     * - 문자열 trim
     * - 빈 문자열 -> null
     * - Boolean null 
     * 현재 객체 값을 꺼냄 -> 공백 제거/빈값 null 변환 -> 다시 현재 객체 저장
     */
    public void normalize() {
        this.productName = trimToNull(this.productName);
        this.productInfo = trimToNull(this.productInfo);
        this.manufacturer = trimToNull(this.manufacturer);

        if (this.isDiscount == null) {
            this.isDiscount = false;
        }

        if (this.isRecommend == null) {
            this.isRecommend = false;
        }
    }

    /**
     * options 필드가 요청에 포함되었는지 여부
     */
    public boolean hasOptionsField() {
        return this.options != null;
    }

    /**
     * 옵션 전체 삭제 요청인지 여부
     * DB에 들어올 값을 공백 보다는 null로 통일
     */
    public boolean isOptionsClearRequest() {
        return this.options != null && this.options.isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductOptionDto {

        @NotBlank(message = "색상은 필수입니다.")
        private String color;

        @NotBlank(message = "카테고리는 필수입니다.")
        private String category;

        @NotNull(message = "성별은 필수입니다.")
        private Gender gender;

        @NotBlank(message = "사이즈는 필수입니다.")
        private String size;

        @NotNull(message = "재고는 필수입니다.")
        @DecimalMin(value = "0", inclusive = true, message = "재고는 0 이상이어야 합니다.")
        private Long stock;
    }
}