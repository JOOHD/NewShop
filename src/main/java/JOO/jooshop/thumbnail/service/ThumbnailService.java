package JOO.jooshop.thumbnail.service;

import JOO.jooshop.global.file.FileStorageService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.thumbnail.model.ProductThumbnailDto;
import JOO.jooshop.thumbnail.repository.ProductThumbnailRepositoryV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ThumbnailService {

    /**
     * 역할:
     * - 썸네일 업로드 / 외부 URL 등록 / 조회 / 삭제 담당
     *
     * 리팩토링 핵심:
     * - Product aggregate root의 addThumbnail/removeThumbnail 메서드와 맞물리도록 정리
     * - 로컬 파일 / 외부 URL 둘 다 지원
     */

    private static final String THUMBNAIL_DIR = "thumbnails";
    private static final String UPLOAD_PREFIX = "/uploads/";

    private final ProductThumbnailRepositoryV1 productThumbnailRepository;
    private final FileStorageService fileStorageService;

    /** 전체 썸네일 DTO 조회 */
    public List<ProductThumbnailDto> getAllThumbnails() {
        return productThumbnailRepository.findAll()
                .stream()
                .map(ProductThumbnailDto::new)
                .collect(Collectors.toList());
    }

    /** 특정 상품의 썸네일 raw path 조회 */
    public List<String> getProductThumbnailPaths(Long productId) {
        return productThumbnailRepository.findByProduct_ProductId(productId)
                .stream()
                .map(ProductThumbnail::getImagePath)
                .toList();
    }

    /** 특정 상품의 썸네일 URL 조회 */
    public List<String> getThumbnailUrls(Long productId) {
        return getProductThumbnailPaths(productId)
                .stream()
                .map(this::toClientUrl)
                .toList();
    }

    /** Product 기준 대표 썸네일 URL 1개 조회 */
    public String getRepresentativeThumbnailUrl(Product product) {
        if (product == null) {
            return null;
        }

        return product.thumbnailsView().stream()
                .map(ProductThumbnail::getImagePath)
                .map(this::toClientUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 로컬 파일 썸네일 1장 업로드 후 Product에 연결 */
    @Transactional
    public void uploadThumbnail(Product product, MultipartFile file) {
        validateProduct(product);

        if (file == null || file.isEmpty()) {
            return;
        }

        try {
            String relativePath = fileStorageService.saveFile(file, THUMBNAIL_DIR);
            ProductThumbnail thumbnail = ProductThumbnail.createThumbnail(relativePath);
            product.addThumbnail(thumbnail);

        } catch (IOException e) {
            log.error("썸네일 업로드 실패: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("썸네일 업로드 중 오류가 발생했습니다.", e);
        }
    }

    /** 로컬 파일 썸네일 여러 장 업로드 */
    @Transactional
    public void uploadThumbnails(Product product, List<MultipartFile> files) {
        validateProduct(product);

        if (files == null || files.isEmpty()) {
            return;
        }

        for (MultipartFile file : files) {
            uploadThumbnail(product, file);
        }
    }

    /** 외부 URL 썸네일 1개 등록 */
    @Transactional
    public void addExternalThumbnail(Product product, String externalImageUrl) {
        validateProduct(product);

        String normalized = normalizeExternalUrl(externalImageUrl);
        product.addThumbnailPath(normalized);
    }

    /** 기존 썸네일 전체 삭제 후 새 파일들로 교체 */
    @Transactional
    public void replaceThumbnails(Product product, List<MultipartFile> newFiles) {
        validateProduct(product);

        deleteAllByProduct(product);
        uploadThumbnails(product, newFiles);
    }

    /** 썸네일 1개 삭제 */
    @Transactional
    public void deleteThumbnail(Long thumbnailId) {
        ProductThumbnail thumbnail = productThumbnailRepository.findById(thumbnailId)
                .orElseThrow(() -> new IllegalArgumentException("해당 썸네일을 찾을 수 없습니다. id=" + thumbnailId));

        deleteLocalFileIfNeeded(thumbnail.getImagePath());

        Product product = thumbnail.getProduct();
        if (product != null) {
            product.removeThumbnail(thumbnail);
        }
    }

    /** 특정 상품의 썸네일 전체 삭제 */
    @Transactional
    public void deleteAllByProduct(Product product) {
        validateProduct(product);

        List<ProductThumbnail> thumbnails = List.copyOf(product.thumbnailsView());

        for (ProductThumbnail thumbnail : thumbnails) {
            deleteLocalFileIfNeeded(thumbnail.getImagePath());
            product.removeThumbnail(thumbnail);
        }
    }

    /** DB 저장 경로를 화면 표시용 URL로 변환 */
    private String toClientUrl(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String trimmed = path.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        if (trimmed.startsWith(UPLOAD_PREFIX)) {
            return trimmed;
        }

        return UPLOAD_PREFIX + trimmed;
    }

    /** 외부 URL 검증 */
    private String normalizeExternalUrl(String url) {
        if (url == null) {
            throw new IllegalArgumentException("외부 URL은 null일 수 없습니다.");
        }

        String trimmed = url.trim();

        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("외부 URL은 비어 있을 수 없습니다.");
        }

        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            throw new IllegalArgumentException("외부 URL 썸네일만 허용됩니다.");
        }

        return trimmed;
    }

    /** 로컬 저장 파일이면 실제 파일 삭제 */
    private void deleteLocalFileIfNeeded(String path) {
        if (path == null || path.isBlank()) {
            return;
        }

        if (path.startsWith("http://") || path.startsWith("https://")) {
            return;
        }

        try {
            fileStorageService.deleteFile(path);
        } catch (Exception e) {
            log.error("썸네일 파일 삭제 실패: {}", path, e);
        }
    }

    /** Product null 방어 */
    private void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product는 null일 수 없습니다.");
        }
    }
}