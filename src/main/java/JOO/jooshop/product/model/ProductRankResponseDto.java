package JOO.jooshop.product.model;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 상품 랭킹 조회용 응답 DTO.
 * 읽기 전용 — Setter 없음.
 * 썸네일이 없는 상품은 productThumbnails = null.
 */
@Getter
public class ProductRankResponseDto {

    private final Long productId;
    private final ProductType productType;
    private final String productName;
    private final BigDecimal price;
    private final Long wishListCount;
    private final Boolean isDiscount;
    private final Integer discountRate;
    private final Boolean isRecommend;
    private final String productThumbnails;

    public ProductRankResponseDto(Product product) {
        this.productId = product.getProductId();
        this.productType = product.getProductType();
        this.productName = product.getProductName();
        this.price = product.getPrice();
        this.wishListCount = product.getWishListCount();
        this.isDiscount = product.isDiscount();
        this.discountRate = product.getDiscountRate();
        this.isRecommend = product.isRecommend();
        // 썸네일이 없는 경우 null 반환 — 기존 modelMapper 방식의 IndexOutOfBoundsException 방지
        this.productThumbnails = product.getProductThumbnails().isEmpty()
                ? null
                : product.getProductThumbnails().get(0).getImagesPath();
    }
}
