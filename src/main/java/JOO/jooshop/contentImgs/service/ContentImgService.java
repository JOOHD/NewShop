package JOO.jooshop.contentImgs.service;

import JOO.jooshop.contentImgs.entity.ContentImages;
import JOO.jooshop.contentImgs.repository.ContentImagesRepository;
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
public class ContentImgService {

    /**
     * [이 클래스의 역할]
     * - 상품 상세 본문 이미지 업로드 / 조회 / 삭제를 담당하는 서비스
     * - 파일 저장/삭제는 FileStorageService에 위임
     * - 엔티티 연결/해제는 Product aggregate root를 통해 처리
     *
     * [기존 -> 리팩토링]
     * - 기존: create(product, ...) / product.getContentImages().add(...) / repository.saveAll(...)
     * - 변경: createContentImage(...) 후 product.addContentImage(...) 로 연결
     * - 결과: 상세 이미지도 썸네일과 동일하게 aggregate root 규칙을 따름
     */

    private static final String CONTENT_IMG_DIR = "contentImgs";

    private final FileStorageService fileStorageService;
    private final ContentImagesRepository contentImagesRepository;

    /** 특정 상품의 본문 이미지 목록 조회 */
    public List<ContentImages> getContentImages(Long productId) {
        return contentImagesRepository.findByProduct_ProductId(productId);
    }

    /** 본문 이미지 1장 업로드 후 Product에 연결 */
    @Transactional
    public void uploadContentImage(Product product, MultipartFile image) {
        validateProduct(product);

        if (image == null || image.isEmpty()) {
            return;
        }

        try {
            String relativePath = fileStorageService.saveFile(image, CONTENT_IMG_DIR);
            ContentImages contentImage = ContentImages.createContentImage(relativePath);

            // aggregate root를 통해 연관관계 연결
            product.addContentImage(contentImage);

        } catch (IOException e) {
            log.error("상세 이미지 업로드 실패: {}", image.getOriginalFilename(), e);
            throw new RuntimeException("상세 이미지 업로드 중 오류가 발생했습니다.", e);
        }
    }

    /** 본문 이미지 여러 장 업로드 후 Product에 연결 */
    @Transactional
    public void uploadContentImages(Product product, List<MultipartFile> images) {
        validateProduct(product);

        if (images == null || images.isEmpty()) {
            return;
        }

        for (MultipartFile image : images) {
            uploadContentImage(product, image);
        }
    }

    /** 외부 URL 본문 이미지 추가 */
    @Transactional
    public void addExternalContentImage(Product product, String externalImageUrl) {
        validateProduct(product);

        String normalized = normalizeExternalUrl(externalImageUrl);
        ContentImages contentImage = ContentImages.createContentImage(normalized);

        // aggregate root를 통해 연관관계 연결
        product.addContentImage(contentImage);
    }

    /** 본문 이미지 전체 교체 */
    @Transactional
    public void replaceContentImages(Product product, List<MultipartFile> newImages) {
        validateProduct(product);

        deleteAllByProduct(product);
        uploadContentImages(product, newImages);
    }

    /** 본문 이미지 1개 삭제 */
    @Transactional
    public void deleteContentImage(Long contentImgId) {
        ContentImages contentImage = contentImagesRepository.findById(contentImgId)
                .orElseThrow(() -> new IllegalArgumentException("해당 상세 이미지를 찾을 수 없습니다. id=" + contentImgId));

        deleteLocalFileIfNeeded(contentImage.getImagePath());

        Product product = contentImage.getProduct();
        if (product != null) {
            product.removeContentImage(contentImage);
        }
    }

    /** 특정 Product의 본문 이미지 전체 삭제 */
    @Transactional
    public void deleteAllByProduct(Product product) {
        validateProduct(product);

        List<ContentImages> contentImages = List.copyOf(product.getContentImages());

        for (ContentImages contentImage : contentImages) {
            deleteLocalFileIfNeeded(contentImage.getImagePath());
            product.removeContentImage(contentImage);
        }
    }

    /** 외부 URL 형식 검증 */
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

    /** 로컬 파일이면 실제 파일 삭제 */
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