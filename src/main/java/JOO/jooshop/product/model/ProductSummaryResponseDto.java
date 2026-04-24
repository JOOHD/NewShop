package JOO.jooshop.product.model;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductSummaryResponseDto  {

    /**
     * 목적: 회원용 API에서 상품 등록/수정 후 반환 혹은 간단 상품 조회용 DTO
     *
     * - 상세 옵션/찜 정보 제외
     * - Admin용 DTO와 혼동되지 않도록 최소 필드만 포함
     */
    private Long productId;
    private String productName;
    private ProductType productType;
    private BigDecimal price;
    private String productInfo;
    private String manufacturer;
    private Boolean isDiscount;
    private Integer discountRate;
    private Boolean isRecommend;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> thumbnails;

    public ProductSummaryResponseDto (Product product) {
        this.productId = product.getProductId();
        this.productName = product.getProductName();
        this.productType = product.getProductType();
        this.price = product.getPrice();
        this.productInfo = product.getProductInfo();
        this.manufacturer = product.getManufacturer();
        this.isDiscount = product.isDiscount();
        this.discountRate = product.getDiscountRate();
        this.isRecommend = product.isRecommend();
        this.createdAt = product.getCreatedAt();
        this.updatedAt = product.getUpdatedAt();
        this.thumbnails = product.getProductThumbnails()
                .stream()
                .map(t -> t.getImagesPath())
                .collect(Collectors.toList());
    }
}
