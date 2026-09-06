package JOO.jooshop.admin.products.service;

import JOO.jooshop.admin.products.model.AdminProductRequestDto;
import JOO.jooshop.admin.products.model.AdminProductResponseDto;
import JOO.jooshop.admin.products.repository.AdminProductRepository;
import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.productDetailImages.service.ProductDetailImageService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.thumbnail.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductService {

    private final AdminProductRepository productRepository;
    private final ThumbnailService thumbnailService;
    private final ProductDetailImageService productDetailImagesService;

    /**
     * 관리자 상품 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AdminProductResponseDto> findAllProduct() {
        return productRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    /**
     * 관리자 상품 등록
     * - 기본 정보 생성
     * - 옵션/썸네일/상세 이미지 반영
     * - 이미지는 파일 업로드 없이 외부 URL(thumbnailUrl/contentUrls)로만 등록
     */
    public AdminProductResponseDto createProduct(AdminProductRequestDto dto) {
        dto.normalizeAndValidate();

        Product product = Product.create(
                dto.getProductName(),
                dto.getProductType(),
                dto.getPrice(),
                dto.getProductInfo(),
                dto.getManufacturer(),
                dto.getIsDiscount(),
                dto.getDiscountRate(),
                dto.getIsRecommend()
        );

        if (dto.hasOptionsField()) {
            product.replaceOptions(toProductVariants(dto.getOptions()));
        }

        productRepository.save(product);

        applyThumbnailUrlIfPresent(product, dto);
        applyContentUrlsIfPresent(product, dto);

        return toResponseDto(product);
    }

    /**
     * 관리자 상품 수정
     * - 기존 상품 조회 후 기본 정보 변경
     * - 옵션/썸네일/상세 이미지 변경 정책 반영 (외부 URL만 지원)
     * - 영속 엔티티 수정이므로 dirty checking으로 반영
     */
    public AdminProductResponseDto updateProduct(Long id, AdminProductRequestDto dto) {
        dto.normalizeAndValidate();

        Product product = productRepository.findWithDetailsByProductId(id)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다."));

        product.changeBasicInfo(
                dto.getProductName(),
                dto.getProductType(),
                dto.getPrice(),
                dto.getProductInfo(),
                dto.getManufacturer(),
                dto.getIsDiscount(),
                dto.getDiscountRate(),
                dto.getIsRecommend()
        );

        applyOptionChanges(product, dto);
        applyThumbnailChanges(product, dto);
        applyProductDetailImageChanges(product, dto);

        return toResponseDto(product);
    }

    /**
     * 관리자 상품 삭제
     */
    public void deleteProduct(Long productId) {
        Product product = productRepository.findWithDetailsByProductId(productId)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다."));

        productRepository.delete(product);
    }

    /**
     * 옵션 변경 정책 적용
     * - null: 옵션 변경 없음
     * - empty: 옵션 전체 삭제
     * - values: 옵션 전체 교체
     */
    private void applyOptionChanges(Product product, AdminProductRequestDto dto) {
        if (!dto.hasOptionsField()) {
            return;
        }

        if (dto.isOptionsClearRequest()) {
            product.clearOptions();
            return;
        }

        product.replaceOptions(toProductVariants(dto.getOptions()));
    }

    /**
     * 썸네일 변경 정책 적용 — 외부 URL만 지원
     */
    private void applyThumbnailChanges(Product product, AdminProductRequestDto dto) {
        boolean hasThumbnailUrl = dto.getThumbnailUrl() != null && !dto.getThumbnailUrl().isBlank();

        if (!hasThumbnailUrl) {
            return;
        }

        product.clearThumbnails();
        thumbnailService.addExternalThumbnail(product, dto.getThumbnailUrl());
    }

    /**
     * 상세 이미지 변경 정책 적용 — 외부 URL만 지원
     */
    private void applyProductDetailImageChanges(Product product, AdminProductRequestDto dto) {
        boolean hasContentUrls = dto.getContentUrls() != null && !dto.getContentUrls().isEmpty();

        if (!hasContentUrls) {
            return;
        }

        product.clearProductDetailImage();
        productDetailImagesService.addExternalProductDetailImage(product, dto.getContentUrls());
    }

    /**
     * 등록 시 썸네일 URL이 있으면 상품에 반영
     */
    private void applyThumbnailUrlIfPresent(Product product, AdminProductRequestDto dto) {
        if (dto.getThumbnailUrl() == null || dto.getThumbnailUrl().isBlank()) {
            return;
        }

        thumbnailService.addExternalThumbnail(product, dto.getThumbnailUrl());
    }

    /**
     * 등록 시 상세 이미지 URL 목록이 있으면 상품에 반영
     */
    private void applyContentUrlsIfPresent(Product product, AdminProductRequestDto dto) {
        if (dto.getContentUrls() == null || dto.getContentUrls().isEmpty()) {
            return;
        }

        productDetailImagesService.addExternalProductDetailImage(product, dto.getContentUrls());
    }

    /**
     * 요청 DTO의 옵션 목록을 ProductVariant 엔티티 목록으로 변환
     */
    private List<ProductVariant> toProductVariants(List<AdminProductRequestDto.ProductVariantDto> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }

        return options.stream()
                .map(this::toProductVariant)
                .toList();
    }

    /**
     * 단일 옵션 DTO를 ProductVariant 엔티티로 변환
     */
    private ProductVariant toProductVariant(AdminProductRequestDto.ProductVariantDto dto) {
        ProductColor color = ProductColor.ofName(dto.getColor());
        Category category = Category.ofName(dto.getCategory());
        Size size = Size.valueOf(dto.getSize());

        return ProductVariant.create(
                color,
                category,
                dto.getGender(),
                size,
                dto.getStock() == null ? 0L : dto.getStock()
        );
    }

    /**
     * Product 엔티티를 관리자 응답 DTO로 변환
     */
    private AdminProductResponseDto toResponseDto(Product product) {
        String thumbnailUrl = product.getProductThumbnails().stream()
                .findFirst()
                .map(ProductThumbnail::getImagesPath)
                .orElse(null);

        return AdminProductResponseDto.from(product, thumbnailUrl);
    }
}