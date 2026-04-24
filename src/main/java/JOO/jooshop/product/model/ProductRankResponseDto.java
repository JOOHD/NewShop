package JOO.jooshop.product.model;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductRankResponseDto {

    private Long productId;           // 상품 고유 번호 (Primary Key)
    private ProductType productType;  // 상품의 유형 (남성/여성/유니섹스 등 ENUM)
    private String productName;       // 상품 이름
    private BigDecimal price;            // 상품 가격
    private Long wishListCount;       // 위시리스트에 추가된 횟수 (인기 상품 기준에 사용될 수 있음)
    private Boolean isDiscount;       // 할인 여부 (true = 할인중, false = 비할인)
    private Integer discountRate;     // 할인율 (퍼센트로 저장, 예: 20 -> 20%)
    private Boolean isRecommend;      // 추천 상품 여부 (true면 추천 상품)
    private String productThumbnails; // 상품 썸네일 이미지 경로나 URL (썸네일)


    public ProductRankResponseDto(Product product) {
        this(
                product.getProductId(),
                product.getProductType(),
                product.getProductName(),
                product.getPrice(),
                product.getWishListCount(),
                product.isDiscount(),
                product.getDiscountRate(),
                product.isRecommend(),
                product.getProductThumbnails().get(0).getImagesPath()// 경로만 가져오기

        );
    }
}
