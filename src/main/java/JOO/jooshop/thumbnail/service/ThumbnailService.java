package JOO.jooshop.thumbnail.service;

import JOO.jooshop.global.image.ImageUrlResolver;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import JOO.jooshop.thumbnail.model.ProductThumbnailDto;
import JOO.jooshop.thumbnail.repository.ProductThumbnailRepositoryV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final String UPLOAD_PREFIX = "/uploads/";

    private final ProductThumbnailRepositoryV1 productThumbnailRepository;
    private final ImageUrlResolver imageUrlResolver;

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
                .map(ProductThumbnail::getImagesPath)
                .toList();
    }

    /** 특정 상품의 썸네일 URL 조회 */
    public List<String> getThumbnailUrls(Long productId) {
        return getProductThumbnailPaths(productId)
                .stream()
                .map(imageUrlResolver::toClientUrl)
                .toList();
    }

    /** Product 기준 대표 썸네일 URL 1개 조회 */
    public String getRepresentativeThumbnailUrl(Product product) {
        if (product == null) {
            return null;
        }

        return product.productThumbnailsView().stream()
                .map(ProductThumbnail::getImagesPath)
                .map(this::toClientUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 외부 URL 썸네일 1개 등록 */
    @Transactional
    public void addExternalThumbnail(Product product, String externalImagesUrl) {
        validateProduct(product);

        String normalized = imageUrlResolver.normalizeExternalUrl(externalImagesUrl);
        product.addThumbnailPath(normalized);
    }

    /** 썸네일 1개 삭제 */
    @Transactional
    public void deleteThumbnail(Long thumbnailId) {
        ProductThumbnail thumbnail = productThumbnailRepository.findById(thumbnailId)
                .orElseThrow(() -> new IllegalArgumentException("해당 썸네일을 찾을 수 없습니다. id=" + thumbnailId));

        Product product = thumbnail.getProduct();

        if (product != null) {
            product.removeThumbnail(thumbnail);
        }
    }

    /** 특정 상품의 썸네일 전체 삭제 */
    @Transactional
    public void deleteAllByProduct(Product product) {
        validateProduct(product);

        List<ProductThumbnail> thumbnails = List.copyOf(product.productThumbnailsView());

        for (ProductThumbnail thumbnail : thumbnails) {
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

    /** Product null 방어 */
    private void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product는 null일 수 없습니다.");
        }
    }
}