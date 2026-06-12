package JOO.jooshop.product.model;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 상품 목록 조회용 응답 DTO.
 * 리스트 화면에 필요한 최소 정보 + 대표 썸네일 포함.
 * 읽기 전용 — Setter 없음.
 */
@Getter
public class ProductListResponseDto {

    private final Long productId;
    private final ProductType productType;
    private final String productName;
    private final BigDecimal price;
    private final LocalDateTime createdAt;
    private final Long wishListCount;
    private final Boolean isDiscount;
    private final Integer discountRate;
    private final Boolean isRecommend;
    private final List<String> productThumbnails;

    public ProductListResponseDto(Product product) {
        this.productId = product.getProductId();
        this.productType = product.getProductType();
        this.productName = product.getProductName();
        this.price = product.getPrice();
        this.createdAt = product.getCreatedAt();
        this.wishListCount = product.getWishListCount();
        this.isDiscount = product.isDiscount();
        this.discountRate = product.getDiscountRate();
        this.isRecommend = product.isRecommend();
        this.productThumbnails = product.getProductThumbnails().stream()
                .map(ProductThumbnail::getImagesPath)
                .collect(Collectors.toList());
    }
}
