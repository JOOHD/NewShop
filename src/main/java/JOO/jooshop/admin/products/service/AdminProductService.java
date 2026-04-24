package JOO.jooshop.admin.products.service;

import JOO.jooshop.admin.products.model.AdminProductRequestDto;
import JOO.jooshop.admin.products.model.AdminProductResponseDto;
import JOO.jooshop.admin.products.repository.AdminProductRepository;
import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.contentImages.service.ContentImagesService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.productManagement.entity.ProductManagement;
import JOO.jooshop.productManagement.entity.enums.Size;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.thumbnail.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductService {

    private final AdminProductRepository productRepository;
    private final ThumbnailService thumbnailService;
    private final ContentImagesService contentImagesService;

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
     * - 업로드 파일이 있으면 URL값보다 파일 기준으로 최종 반영
     */
    public AdminProductResponseDto createProduct(
            AdminProductRequestDto dto,
            MultipartFile thumbnail,
            List<MultipartFile> contentImages
    ) {
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
            product.replaceOptions(toProductManagements(dto.getOptions()));
        }

        applyThumbnailUrlIfPresent(product, dto);
        applyContentUrlsIfPresent(product, dto);

        productRepository.save(product);

        applyThumbnailFile(product, thumbnail);
        applyContentImagesFiles(product, contentImages);

        return toResponseDto(product);
    }

    /**
     * 관리자 상품 수정
     * - 기존 상품 조회 후 기본 정보 변경
     * - 옵션/썸네일/상세 이미지 변경 정책 반영
     * - 영속 엔티티 수정이므로 dirty checking으로 반영
     */
    public AdminProductResponseDto updateProduct(
            Long id,
            AdminProductRequestDto dto,
            MultipartFile thumbnail,
            List<MultipartFile> contentImages
    ) {
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
        applyThumbnailChanges(product, dto, thumbnail);
        applyContentImagesChanges(product, dto, contentImages);

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

        product.replaceOptions(toProductManagements(dto.getOptions()));
    }

    /**
     * 썸네일 변경 정책 적용
     * - 파일 또는 URL이 들어오면 기존 썸네일 제거 후 새 값 반영
     * - 파일이 있으면 파일 업로드를 우선 적용
     */
    private void applyThumbnailChanges(Product product,
                                       AdminProductRequestDto dto,
                                       MultipartFile thumbnail) {

        boolean hasThumbnailFile = thumbnail != null && !thumbnail.isEmpty();
        boolean hasThumbnailUrl = dto.getThumbnailUrl() != null && !dto.getThumbnailUrl().isBlank();

        if (!hasThumbnailFile && !hasThumbnailUrl) {
            return;
        }

        product.clearThumbnails();

        if (hasThumbnailFile) {
            thumbnailService.uploadThumbnail(product, thumbnail);
            return;
        }

        product.addThumbnailPath(dto.getThumbnailUrl());
    }

    /**
     * 상세 이미지 변경 정책 적용
     * - 파일 목록 또는 URL 목록이 들어오면 기존 상세 이미지 제거 후 새 값 반영
     * - 파일 목록이 있으면 파일 업로드를 우선 적용
     */
    private void applyContentImagesChanges(Product product,
                                          AdminProductRequestDto dto,
                                          List<MultipartFile> contentImages) {

        boolean hasContentFiles = contentImages != null;
        boolean hasContentUrls = dto.getContentUrls() != null;

        if (!hasContentFiles && !hasContentUrls) {
            return;
        }

        product.clearContentImages();

        if (hasContentFiles && !contentImages.isEmpty()) {
            contentImagesService.uploadcontentImages(product, contentImages);
            return;
        }

        if (hasContentUrls && !dto.getContentUrls().isEmpty()) {
            for (String contentUrl : dto.getContentUrls()) {
                if (contentUrl != null && !contentUrl.isBlank()) {
                    product.addContentImagesPath(contentUrl);
                }
            }
        }
    }

    /**
     * 등록 시 썸네일 URL이 있으면 상품에 반영
     */
    private void applyThumbnailUrlIfPresent(Product product, AdminProductRequestDto dto) {
        if (dto.getThumbnailUrl() == null || dto.getThumbnailUrl().isBlank()) {
            return;
        }

        product.addThumbnailPath(dto.getThumbnailUrl());
    }

    /**
     * 등록 시 상세 이미지 URL 목록이 있으면 상품에 반영
     */
    private void applyContentUrlsIfPresent(Product product, AdminProductRequestDto dto) {
        if (dto.getContentUrls() == null || dto.getContentUrls().isEmpty()) {
            return;
        }

        for (String contentUrl : dto.getContentUrls()) {
            if (contentUrl != null && !contentUrl.isBlank()) {
                product.addContentImagesPath(contentUrl);
            }
        }
    }

    /**
     * 등록 시 썸네일 파일이 있으면 업로드 후 반영
     */
    private void applyThumbnailFile(Product product, MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()) {
            return;
        }

        product.clearThumbnails();
        thumbnailService.uploadThumbnail(product, thumbnail);
    }

    /**
     * 등록 시 상세 이미지 파일 목록이 있으면 업로드 후 반영
     */
    private void applyContentImagesFiles(Product product, List<MultipartFile> contentImages) {
        if (contentImages == null || contentImages.isEmpty()) {
            return;
        }

        product.clearContentImages();
        contentImagesService.uploadcontentImages(product, contentImages);
    }

    /**
     * 요청 DTO의 옵션 목록을 ProductManagement 엔티티 목록으로 변환
     */
    private List<ProductManagement> toProductManagements(List<AdminProductRequestDto.ProductManagementDto> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }

        return options.stream()
                .map(this::toProductManagement)
                .toList();
    }

    /**
     * 단일 옵션 DTO를 ProductManagement 엔티티로 변환
     */
    private ProductManagement toProductManagement(AdminProductRequestDto.ProductManagementDto dto) {
        ProductColor color = ProductColor.ofName(dto.getColor());
        Category category = Category.ofName(dto.getCategory());
        Size size = Size.valueOf(dto.getSize());

        return ProductManagement.create(
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