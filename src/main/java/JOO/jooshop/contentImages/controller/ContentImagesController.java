package JOO.jooshop.contentImages.controller;

import JOO.jooshop.contentImages.entity.ContentImages;
import JOO.jooshop.contentImages.service.ContentImagesService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static JOO.jooshop.global.exception.ResponseMessageConstants.PRODUCT_NOT_FOUND;
import static JOO.jooshop.global.exception.ResponseMessageConstants.DELETE_SUCCESS;

@RestController
@RequestMapping("/api/v1/product/Images")
@RequiredArgsConstructor
public class ContentImagesController {

    private final ContentImagesService contentImagesService;
    private final ProductRepository productRepository;

    // 이미지 업로드 (MultipartFile)
    @PostMapping("/upload")
    public ResponseEntity<String> uploadContentImg(
            @RequestParam("productId") Long productId,
            @RequestParam("contentImages") List<MultipartFile> contentImages
    ) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND));

        contentImagesService.uploadcontentImages(product, contentImages);
        return ResponseEntity.status(HttpStatus.CREATED).body("이미지 업로드 완료");
    }

    // 이미지 삭제
    @DeleteMapping("/delete/{contentImgId}")
    public ResponseEntity<String> deleteContentImg(@PathVariable("contentImgId") Long contentImgId) {
        contentImagesService.deleteContentImages(contentImgId);
        return ResponseEntity.status(HttpStatus.OK).body(DELETE_SUCCESS);
    }

    // 상품별 이미지 조회 (경로 리스트)
    @GetMapping("/{productId}")
    public ResponseEntity<List<String>> getProductContentImages(@PathVariable("productId") Long productId) {
        List<ContentImages> contentImages = contentImagesService.getContentImages(productId);

        if (!contentImages.isEmpty()) {
            List<String> ImagesPaths = contentImages.stream()
                    .map(ContentImages::getImagesPath)
                    .collect(Collectors.toList());
            return ResponseEntity.ok().body(ImagesPaths);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
