package JOO.jooshop.contentImages.service;

import JOO.jooshop.contentImages.entity.ContentImages;
import JOO.jooshop.contentImages.repository.ContentImagesRepository;
import JOO.jooshop.global.image.ImageUrlResolver;
import JOO.jooshop.product.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentImagesService {

    /**
     * 역할:
     * - 상품 상세 이미지 업로드 / 외부 URL 등록 / 조회 / 삭제 담당
     *
     * 리팩토링 핵심:
     * - Product aggregate root의 addContentImages/removeContentImages 메서드와 맞물리도록 정리
     * - 썸네일 서비스와 네이밍/구조를 최대한 대칭으로 맞춤
     * - 로컬 파일 / 외부 URL 둘 다 지원
     */

    private static final String UPLOAD_PREFIX = "/uploads/";

    private final ImageUrlResolver imageUrlResolver;
    private final ContentImagesRepository contentImagesRepository;

    /** 특정 상품의 상세 이미지 목록 조회 */
    public List<ContentImages> getContentImages(Long productId) {
        return contentImagesRepository.findByProduct_ProductId(productId);
    }
    
    /** 특정 상품의 상세 이미지 URL 목록 조회 */
    public List<String> getContentImagesUrls(Long productId) {
        return getContentImages(productId)
                .stream()
                .map(ContentImages::getImagesPath)
                .map(imageUrlResolver::toClientUrl)
                .toList();
    }

    /** 외부 URL 상세 여러 개 등록 */
    @Transactional
    public void addExternalContentImages(Product product, List<String> externalImagesUrls) {
        validateProduct(product);

        if (externalImagesUrls == null || externalImagesUrls.isEmpty()) {
            return;
        }

        for (String externalImagesUrl : externalImagesUrls) {
            String normalized = imageUrlResolver.normalizeExternalUrl(externalImagesUrl);
            product.addContentImagesPath(normalized);
        }
    }

    /** 상세 이미지 1개 삭제 */
    @Transactional
    public void deleteContentImages(Long contentImgId) {
        ContentImages contentImages = contentImagesRepository.findById(contentImgId)
                .orElseThrow(() -> new IllegalArgumentException("해당 상세 이미지를 찾을 수 없습니다. id=" + contentImgId));

        Product product = contentImages.getProduct();
        if (product != null) {
            product.removeContentImages(contentImages);
        }
    }

    /** 특정 상품의 상세 이미지 전체 삭제 */
    @Transactional
    public void deleteAllByProduct(Product product) {
        validateProduct(product);

        List<ContentImages> contentImages = List.copyOf(product.contentImagesView());

        for (ContentImages contentImage : contentImages) {
            product.removeContentImages(contentImage);
        }
    }

    /** 조회 후, 브라우저용 URL 변환 */
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