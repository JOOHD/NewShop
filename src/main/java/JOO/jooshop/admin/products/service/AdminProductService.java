package JOO.jooshop.admin.products.service;

import JOO.jooshop.admin.products.model.AdminProductRequestDto;
import JOO.jooshop.admin.products.model.AdminProductResponseDto;
import JOO.jooshop.admin.products.repository.AdminProductRepository;
import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.contentImgs.entity.ContentImages;
import JOO.jooshop.contentImgs.entity.enums.UploadType;
import JOO.jooshop.contentImgs.repository.ContentImagesRepository;
import JOO.jooshop.contentImgs.service.ContentImgService;
import JOO.jooshop.global.file.FileStorageService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.productManagement.entity.ProductManagement;
import JOO.jooshop.productManagement.entity.enums.Size;
import JOO.jooshop.productManagement.repository.ProductManagementRepository;
import JOO.jooshop.thumbnail.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminProductService {

    private final ThumbnailService thumbnailService;
    private final ContentImgService contentImgService;
    private final AdminProductRepository productRepository;
    private final ProductManagementRepository productManagementRepository;

    private final ContentImagesRepository contentImagesRepository;
    private final FileStorageService fileStorageService;

    /* =========================
       Query
    ========================= */

    @Transactional(readOnly = true)
    public List<AdminProductResponseDto> findAllProduct() {
        // findAll -> ForAdminList, jpa 기본 메서드 지양(n+1, 과부화 방지)
        return productRepository.findAllForAdminList() // 1. 조회
                .stream()
                .map(this::toResponseDto)              // 2. 변환
                .collect(Collectors.toList());         // 3. 수집
    }

    private AdminProductResponseDto toResponseDto(Product product) {
        String thumbnailUrl = thumbnailService.pickRepresentativeThumbnailUrl(product);

        return new AdminProductResponseDto(
                product.getProductId(),
                product.getProductName(),
                product.getProductType(),
                product.getPrice(),
                product.getDiscountRate(),
                product.getProductInfo(),
                thumbnailUrl,
                product.getCreatedAt()
        );
    }

    /* =========================
       Command (create / update)
    ========================= */

    public AdminProductResponseDto createProduct(
            AdminProductRequestDto dto,
            MultipartFile thumbnail,
            List<MultipartFile> contentImages
    ) {
        dto.normalizeAndValidate();

        Product product = new Product(dto);
        Product saved = productRepository.save(product);

        replaceOptions(saved, dto.getOptions());

        if (thumbnail != null && !thumbnail.isEmpty()) {
            thumbnailService.uploadThumbnailImages(saved, thumbnail);
        }
        if (contentImages != null && !contentImages.isEmpty()) {
            contentImgService.uploadContentImages(saved, contentImages, UploadType.PRODUCT);
        }

        return toResponseDto(saved);
    }

    public AdminProductResponseDto updateProduct(
            Long id,
            AdminProductRequestDto dto,
            MultipartFile thumbnail,
            List<MultipartFile> contentImages
    ) {
        dto.normalizeAndValidate();

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다."));

        product.updateFromDto(dto);

        replaceOptions(product, dto.getOptions());

        if (thumbnail != null && !thumbnail.isEmpty()) {
            thumbnailService.uploadThumbnailImages(product, thumbnail);
        }
        if (contentImages != null && !contentImages.isEmpty()) {
            contentImgService.uploadContentImages(product, contentImages, UploadType.PRODUCT);
        }

        return toResponseDto(product);
    }

    private void replaceOptions(Product product, List<AdminProductRequestDto.ProductManagementDto> options) {
        if (options == null) return; // null이면 변경 없음

        Long productId = product.getProductId();

        productManagementRepository.deleteByProductId(productId);

        if (options.isEmpty()) return;

        List<ProductManagement> newOptions = options.stream()
                .map(opt -> toProductManagement(product, opt))
                .toList();

        productManagementRepository.saveAll(newOptions);
    }

    private ProductManagement toProductManagement(Product product, AdminProductRequestDto.ProductManagementDto dto) {
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

    /* =========================
       Command (delete) - ✅ 최선
    ========================= */

    /**
     * ✅ 최선 정책:
     * - DB 정리(옵션/이미지/상품)는 반드시 완료한다.
     * - 파일 삭제는 Best-effort로 시도하고 실패해도 진행한다(로그/추적).
     * - 이유: 파일 시스템은 DB 트랜잭션 롤백 대상이 아님.
     */
    public void deleteProduct(Long productId) {
        // 0) 상품 존재 확인(없으면 조용히 끝낼지/예외낼지 정책 선택)
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("상품이 존재하지 않습니다."));

        // 1) 옵션 삭제 (FK 안전)
        productManagementRepository.deleteByProductId(productId);

        // 2) 썸네일 파일 + DB 삭제 (ThumbnailService 내부도 best-effort)
        thumbnailService.deleteAllThumbnailsByProductId(productId);

        // 3) 상세 이미지 파일 삭제 best-effort + DB 삭제
        deleteAllContentImagesByProductIdBestEffort(productId);

        // 4) Product 삭제 (DB 정리의 최종 단계)
        productRepository.delete(product);
    }

    private void deleteAllContentImagesByProductIdBestEffort(Long productId) {
        List<ContentImages> imgs = contentImagesRepository.findByProduct_ProductId(productId);
        if (imgs == null || imgs.isEmpty()) return;

        List<String> failedDeletes = new ArrayList<>();

        for (ContentImages img : imgs) {
            String path = img.getImagePath();
            if (path == null || path.isBlank()) continue;

            // 외부 URL은 파일 시스템 삭제 대상 아님(혼재 방어)
            String t = path.trim();
            if (t.startsWith("http://") || t.startsWith("https://")) continue;

            try {
                fileStorageService.deleteFile(t);
            } catch (Exception e) {
                failedDeletes.add(t);
                log.error("상세 이미지 파일 삭제 실패: path={}", t, e);
            }
        }

        // DB는 반드시 정리
        contentImagesRepository.deleteAllInBatch(imgs);

        if (!failedDeletes.isEmpty()) {
            // 운영 관점: 나중에 청소할 수 있게 한 번 더 요약 로그
            log.warn("[AdminProduct] content image file delete failures. productId={}, count={}, paths={}",
                    productId, failedDeletes.size(), failedDeletes);
        }
    }
}
