package JOO.jooshop.product.model;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductListResponseDto {

    /**
     * 상품 목록 조회용 DTO
     * - 상품 리스트 화면에서 필요한 최소 정보만 제공
     * - 대표 썸네일 포함
     */

    private Long productId;
    private ProductType productType;
    private String productName;
    private BigDecimal price;
    private LocalDateTime createdAt;
    private Long wishListCount;
    private Boolean isDiscount;
    private Integer discountRate;
    private Boolean isRecommend;
    private List<String> productThumbnails; // 썸네일 리스트 추가

    public ProductListResponseDto(Product product) {
        this(
                product.getProductId(),
                product.getProductType(),
                product.getProductName(),
                product.getPrice(),
                product.getCreatedAt(),
                product.getWishListCount(),
                product.isDiscount(),
                product.getDiscountRate(),
                product.isRecommend(),
                product.getProductThumbnails().stream()
                        .map(ProductThumbnail::getImagesPath)
                        .collect(Collectors.toList())// 경로만 가져오기

        );
    }

}
