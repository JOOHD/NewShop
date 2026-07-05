package JOO.jooshop.product.service;

import JOO.jooshop.contentImages.service.ContentImagesService;
import JOO.jooshop.global.authorization.RequiresRole;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.model.ProductColorDto;
import JOO.jooshop.product.model.ProductDetailResponseDto;
import JOO.jooshop.product.model.ProductListResponseDto;
import JOO.jooshop.product.model.ProductRequestDto;
import JOO.jooshop.product.repository.ProductColorRepository;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.productManagement.entity.ProductManagement;
import JOO.jooshop.productManagement.entity.enums.Size;
import JOO.jooshop.thumbnail.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.lang.Nullable;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static JOO.jooshop.global.exception.ResponseMessageConstants.PRODUCT_NOT_FOUND;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
@Slf4j
public class ProductServiceV1 {

    private final ProductRepository productRepository;
    private final ProductColorRepository productColorRepository;
    private final ThumbnailService thumbnailService;
    private final ContentImagesService contentImagesService;
    private final ProductRankingService productRankingService;
    private final RecentlyViewedService recentlyViewedService;

    /**
     * 상품 등록
     */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public Long createProduct(ProductRequestDto requestDto,
                              @Nullable MultipartFile thumbnail,
                              @Nullable List<MultipartFile> contentImages) {

        // Product product = new Product(requestDto); 
        // 생성자 외부 생성 지양 -> Aggregate Root로 팩토리 메서드 사용
        // controller에서 받아온 request를 꺼내는 작업
        Product product = Product.create(
                requestDto.getProductName(),
                requestDto.getProductType(),
                requestDto.getPrice(),
                requestDto.getProductInfo(),
                requestDto.getManufacturer(),
                requestDto.getIsDiscount(),
                requestDto.getDiscountRate(),
                requestDto.getIsRecommend()
        );

        if (requestDto.getOptions() != null && !requestDto.getOptions().isEmpty()) {
            product.replaceOptions(toProductManagements(requestDto));
        }

        productRepository.save(product);

        applyThumbnail(product, thumbnail);
        applyContentImages(product, contentImages);

        return product.getProductId();
    }

    /**
     * 상품 수정
     */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public ProductDetailResponseDto updateProduct(Long productId,
                                                  ProductRequestDto updatedDto,
                                                  @Nullable MultipartFile thumbnail,
                                                  @Nullable List<MultipartFile> contentImages) {

        Product product = productRepository.findProductWithDetailsByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND));

        product.changeBasicInfo(
                updatedDto.getProductName(),
                updatedDto.getProductType(),
                updatedDto.getPrice(),
                updatedDto.getProductInfo(),
                updatedDto.getManufacturer(),
                updatedDto.getIsDiscount(),
                updatedDto.getDiscountRate(),
                updatedDto.getIsRecommend()
        );

        if (updatedDto.getOptions() != null) {
            if (updatedDto.getOptions().isEmpty()) {
                product.clearOptions();
            } else {
                product.replaceOptions(toProductManagements(updatedDto));
            }
        }

        if (thumbnail != null && !thumbnail.isEmpty()) {
            product.clearThumbnails();
            thumbnailService.uploadThumbnail(product, thumbnail);
        }

        if (contentImages != null) {
            product.clearContentImages();

            if (!contentImages.isEmpty()) {
                contentImagesService.uploadcontentImages(product, contentImages);
            }
        }

        return new ProductDetailResponseDto(product);
    }

    /**
     * 상품 목록 조회(전체)
     */
    @Transactional(readOnly = true)
    public List<ProductListResponseDto> getAllProducts() {
        return productRepository.findAllWithThumbnails()
                .stream()
                .map(ProductListResponseDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 상품 상세 조회
     */
    @Transactional(readOnly = true)
    public ProductDetailResponseDto productDetail(Long productId) {
        Product product = productRepository.findProductWithDetailsByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND));

        productRankingService.increaseProductViews(productId);  // 전체 조회수 집계 (랭킹용)
        recentlyViewedService.recordIfAuthenticated(productId); // 개인 최근 본 상품 기록

        ProductDetailResponseDto dto = new ProductDetailResponseDto(product);

        // Redis ZSet에서 현재 조회수 조회 후 DTO에 반영
        long viewCount = productRankingService.getProductViewCount(productId);
        dto.setViewCount(viewCount);

        List<ProductManagement> options = product.getProductManagements();
        if (!options.isEmpty()) {
            dto.withInventoryId(options.get(0).getInventoryId());
        }

        return dto;
    }

    /**
     * 상품 삭제
     */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public void deleteProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND));
        productRepository.delete(product);
    }

    /**
     * 색상 등록
     */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public Long createColor(ProductColorDto request) {
        ProductColor color = ProductColor.ofName(request.getColor());
        productColorRepository.save(color);
        return color.getColorId();
    }

    /**
     * 색상 삭제
     */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public void deleteColor(Long colorId) {
        ProductColor color = productColorRepository.findById(colorId)
                .orElseThrow(() -> new NoSuchElementException("해당 색상을 찾을 수 없습니다. Id : " + colorId));
        productColorRepository.delete(color);
    }

    private void applyThumbnail(Product product, @Nullable MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()) {
            return;
        }

        product.clearThumbnails();
        thumbnailService.uploadThumbnail(product, thumbnail);
    }

    private void applyContentImages(Product product, @Nullable List<MultipartFile> contentImages) {
        if (contentImages == null || contentImages.isEmpty()) {
            return;
        }

        product.clearContentImages();
        contentImagesService.uploadContentImages(product, contentImages);
    }

    private List<ProductManagement> toProductManagements(ProductRequestDto dto) {
        if (dto.getOptions() == null || dto.getOptions().isEmpty()) {
            return List.of();
        }

        return dto.getOptions().stream()
                .map(option -> ProductManagement.create(
                        ProductColor.ofName(option.getColor()),
                        JOO.jooshop.categorys.entity.Category.ofName(option.getCategory()),
                        option.getGender(),
                        Size.valueOf(option.getSize()),
                        option.getStock() == null ? 0L :option.getStock()
                ))
                .toList();
    }
}
