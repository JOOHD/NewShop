package JOO.jooshop.product.model;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.wishList.model.WishListDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(of = "productId")
@AllArgsConstructor
public class ProductDetailResponseDto {
    /**
     * 목적: 회원용 상품 상세 조회 API에서 반환되는 DTO
     *
     * - 옵션, 썸네일, 찜한 사용자 정보까지 포함
     * - HTML 렌더링보다는 JSON API 응답용
     */

    private Long productId;                 // 상품 PK
    ProductType productType;                // 성별
    private String productName;             // 상품명
    private BigDecimal price;               // 상품 가격
    private String productInfo;             // 상품 정보
    private LocalDateTime createdAt;        // 생성일
    private LocalDateTime updatedAt;        // 수정일
    private String manufacturer;            // 제조자
    private boolean isDiscount;             // 할인여부 (true: 할인 중, false: 할인 아님)
    private Integer discountRate;           // 할인율
    private boolean isRecommend;            // 추천 여부 (true: 추천 상품, false: 일반 상품)
    private List<WishListDto> wishLists;    // 찜한 사용자 목록 (DTO LIST)
    private Long wishListCount;             // 찜한 수

    // 상세용 필드 추가
    private List<ProductVariant> options;   // 상품 옵션
    private List<String> productThumbnails;   // 썸네일 경로
    private Long variantId;                   // 기본 옵션 variantId (ProductVariant PK)
    private String thumbnailUrl;              // 썸네일
    private long viewCount;                   // Redis 기반 조회수

    public ProductDetailResponseDto(Product product) {
        this.productId = product.getProductId();
        this.productName = product.getProductName();
        this.price = product.getPrice();
        this.productInfo = product.getProductInfo();
        this.manufacturer = product.getManufacturer();
        this.isDiscount = product.isDiscount();
        this.discountRate = product.getDiscountRate();
        this.isRecommend = product.isRecommend();
        this.createdAt = product.getCreatedAt();
        this.updatedAt = product.getUpdatedAt();
        this.wishLists = product.getWishLists() != null
                ? product.getWishLists().stream().map(WishListDto::new).collect(Collectors.toList())
                : Collections.emptyList();
        this.wishListCount = product.getWishListCount();
        this.options = product.getProductVariants();
        this.productThumbnails = product.getProductThumbnails().stream()
                .map(ProductThumbnail::getImagesPath)
                .collect(Collectors.toList());
        this.thumbnailUrl = this.productThumbnails.isEmpty() ? "" : this.productThumbnails.get(0);
        this.variantId = !options.isEmpty() ? options.get(0).getVariantId() : null;
    }

    /**
     *  builder 스타일 체이닝 메서드
     *
     *  ProductDetailResponseDto 객체의 variantId 필드를 설정하고,
     *  메서드 체이닝이 가능하도록 현재 객체를 반환한다.
     */
    public ProductDetailResponseDto withVariantId(Long variantId) {
        this.variantId = variantId;
        return this; // ProductDetailResponseDto 객체
    }

    /**
     *  상품 옵션에서 사이즈별 대표 옵션(ProductVariant) 목록 조회
     *
     *  [버그 수정] 예전엔 Size enum만 뽑아서 반환했는데, Size.description 필드가
     *  주석 처리되어 있어 화면(productDetail.html)에서 dto.size.description /
     *  dto.variantId 로 접근하면 그런 프로퍼티가 없어 렌더링 시 예외가 났다.
     *  사이즈 버튼은 장바구니 담기에 필요한 variantId를 반드시 가지고 있어야 하므로,
     *  Size가 아니라 ProductVariant 자체를 사이즈 기준으로 중복 제거해서 반환한다.
     *  같은 사이즈에 재고 있는 옵션과 품절 옵션이 섞여 있으면, 재고 있는 쪽을 우선한다.
     */
    public List<ProductVariant> getSizes() {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Size, ProductVariant> bySize = new LinkedHashMap<>();
        for (ProductVariant option : options) {
            bySize.merge(option.getSize(), option,
                    (existing, candidate) -> existing.isSoldOut() && !candidate.isSoldOut() ? candidate : existing);
        }
        return new ArrayList<>(bySize.values());
    }

}

