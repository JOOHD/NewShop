package JOO.jooshop.wishList.model;

import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.wishList.entity.WishList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WishListResponseDto {
    private Long wishListId;
    private Long productId;
    private ProductType productType;
    private String productName;
    private BigDecimal price;
    private String productInfo;
    private Boolean isDiscount;
    private Boolean isRecommend;
    private List<String> productThumbnails;

    public WishListResponseDto(WishList wishList) {
        this(
                wishList.getWishListId(),
                wishList.getProduct().getProductId(),
                wishList.getProduct().getProductType(),
                wishList.getProduct().getProductName(),
                wishList.getProduct().getPrice(),
                wishList.getProduct().getProductInfo(),
                wishList.getProduct().isDiscount(),
                wishList.getProduct().isRecommend(),
                wishList.getProduct().getProductThumbnails().stream().map(ProductThumbnail::getImagesPath).toList()
        );
    }
}
