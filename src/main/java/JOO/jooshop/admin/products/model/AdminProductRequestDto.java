package JOO.jooshop.admin.products.model;

import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.product.entity.enums.ProductType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AdminProductRequestDto {

    private String productName;
    private ProductType productType;
    private BigDecimal price;
    private String productInfo;
    private String manufacturer;

    private Boolean isDiscount = false;
    private Integer discountRate = 0;
    private Boolean isRecommend = false;

    private String thumbnailUrl;
    private List<String> contentUrls;

    private List<ProductManagementDto> options;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ProductManagementDto {
        private String color;
        private String category;
        private String size;
        private Gender gender;
        private Long stock;

        public void normalize() {
            if (color != null) color = color.trim();
            if (category != null) category = category.trim();
            if (size != null) size = size.trim();
        }
    }

    public void normalizeAndValidate() {
        if (productName != null) productName = productName.trim();
        if (productInfo != null) productInfo = productInfo.trim();
        if (manufacturer != null) manufacturer = manufacturer.trim();
        if (thumbnailUrl != null) thumbnailUrl = thumbnailUrl.trim();

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName is required");
        }
        if (price == null) {
            throw new IllegalArgumentException("price is required");
        }
        if (productType == null) {
            throw new IllegalArgumentException("productType is required");
        }

        boolean discount = Boolean.TRUE.equals(isDiscount);
        if (!discount) {
            discountRate = 0;
        } else {
            if (discountRate == null) discountRate = 0;
            if (discountRate < 0 || discountRate > 100) {
                throw new IllegalArgumentException("discountRate must be between 0 and 100");
            }
        }

        if (options != null) {
            for (ProductManagementDto opt : options) {
                if (opt == null) continue;
                opt.normalize();

                if (opt.gender == null) {
                    throw new IllegalArgumentException("option.gender is required");
                }
                if (opt.size == null || opt.size.isBlank()) {
                    throw new IllegalArgumentException("option.size is required");
                }
                if (opt.color == null || opt.color.isBlank()) {
                    throw new IllegalArgumentException("option.color is required");
                }
                if (opt.category == null || opt.category.isBlank()) {
                    throw new IllegalArgumentException("option.category is required");
                }
                if (opt.stock != null && opt.stock < 0) {
                    throw new IllegalArgumentException("option.stock must be >= 0");
                }
            }
        }
    }

    public boolean hasOptionsField() {
        return options != null;
    }

    public boolean isOptionsClearRequest() {
        return options != null && options.isEmpty();
    }
}