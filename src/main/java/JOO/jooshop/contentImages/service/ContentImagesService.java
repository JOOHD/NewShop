package JOO.jooshop.contentImages.service;

import JOO.jooshop.contentImages.entity.ContentImages;
import JOO.jooshop.contentImages.repository.ContentImagesRepository;
import JOO.jooshop.global.file.FileStorageService;
import JOO.jooshop.product.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentImagesService {

    /**
     * 역할:
     * - 상품 본문 이미지 업로드 / 외부 URL 등록 / 조회 / 삭제 담당
     *
     * 리팩토링 핵심:
     * - Product aggregate root의 addContentImages/removeContentImages 메서드와 맞물리도록 정리
     * - 썸네일 서비스와 네이밍/구조를 최대한 대칭으로 맞춤
     */

    private static final String CONTENT_IMG_DIR = "contentImages";

    private final FileStorageService fileStorageService;
    private final ContentImagesRepository contentImagesRepository;

    /** 특정 상품의 본문 이미지 목록 조회 */
    public List<ContentImages> getContentImages(Long productId) {
        return contentImagesRepository.findByProduct_ProductId(productId);
    }

    /** 로컬 파일 본문 이미지 1장 업로드 후 Product에 연결 */
    @Transactional
    public void uploadContentImages(Product product, List<MultipartFile> Images) {
        validateProduct(product);

        if (Images == null || Images.isEmpty()) {
            return;
        }

        try {
            String relativePath = fileStorageService.saveFile(Images, CONTENT_IMG_DIR);
            ContentImages contentImages = ContentImages.createContentImages(relativePath);
            product.addContentImages(contentImages);

        } catch (IOException e) {
            log.error("상세 이미지 업로드 실패: {}", Images.getOriginalFilename(), e);
            throw new RuntimeException("상세 이미지 업로드 중 오류가 발생했습니다.", e);
        }
    }

    /** 로컬 파일 본문 이미지 여러 장 업로드 */
    @Transactional
    public void uploadcontentImages(Product product, List<MultipartFile> Imagess) {
        validateProduct(product);

        if (Imagess == null || Imagess.isEmpty()) {
            return;
        }

        for (MultipartFile image : images) {
            uploadContentImages(product, Images);
        }
    }

    /** 외부 URL 본문 이미지 1개 등록 */
    @Transactional
    public void addExternalContentImages(Product product, String externalImagesUrl) {
        validateProduct(product);

        String normalized = normalizeExternalUrl(externalImagesUrl);
        product.addContentImagesPath(normalized);
    }

    /** 기존 본문 이미지 전체 삭제 후 새 이미지들로 교체 */
    @Transactional
    public void replacecontentImages(Product product, List<MultipartFile> newImagess) {
        validateProduct(product);

        deleteAllByProduct(product);
        uploadcontentImages(product, newImagess);
    }

    /** 본문 이미지 1개 삭제 */
    @Transactional
    public void deleteContentImages(Long contentImgId) {
        ContentImages contentImages = contentImagesRepository.findById(contentImgId)
                .orElseThrow(() -> new IllegalArgumentException("해당 상세 이미지를 찾을 수 없습니다. id=" + contentImgId));

        deleteLocalFileIfNeeded(contentImages.getImagesPath());

        Product product = contentImages.getProduct();
        if (product != null) {
            product.removeContentImages(contentImages);
        }
    }

    /** 특정 상품의 본문 이미지 전체 삭제 */
    @Transactional
    public void deleteAllByProduct(Product product) {
        validateProduct(product);

        List<ContentImages> contentImages = List.copyOf(product.contentImagesView());

        for (ContentImages contentImages : contentImages) {
            deleteLocalFileIfNeeded(contentImages.getImagesPath());
            product.removeContentImages(contentImages);
        }
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
            throw new IllegalArgumentException("외부 URL 본문 이미지만 허용됩니다.");
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
            log.error("상세 이미지 파일 삭제 실패: {}", path, e);
        }
    }

    /** Product null 방어 */
    private void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("product는 null일 수 없습니다.");
        }
    }
}