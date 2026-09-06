package JOO.jooshop.productDetailImages.controller;

import JOO.jooshop.productDetailImages.entity.ProductDetailImage;
import JOO.jooshop.productDetailImages.service.ProductDetailImageService;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import static JOO.jooshop.global.exception.ResponseMessageConstants.PRODUCT_NOT_FOUND;
import static JOO.jooshop.global.exception.ResponseMessageConstants.DELETE_SUCCESS;

@RestController
@RequestMapping("/api/v1/product/Images")
@RequiredArgsConstructor
public class ProductDetailImageController {

    private final ProductDetailImageService productDetailImagesService;
    private final ProductRepository productRepository;

    // 이미지 등록 (외부 URL 목록)
    @PostMapping("/upload")
    public ResponseEntity<String> uploadContentImg(
            @RequestParam("productId") Long productId,
            @RequestParam("contentImageUrls") List<String> contentImageUrls
    ) {
        Product product = productRepository.findByProductId(productId)
                .orElseThrow(() -> new NoSuchElementException(PRODUCT_NOT_FOUND));

        productDetailImagesService.addExternalProductDetailImage(product, contentImageUrls);
        return ResponseEntity.status(HttpStatus.CREATED).body("이미지 등록 완료");
    }

    // 이미지 삭제
    @DeleteMapping("/delete/{contentImgId}")
    public ResponseEntity<String> deleteContentImg(@PathVariable("contentImgId") Long contentImgId) {
        productDetailImagesService.deleteProductDetailImage(contentImgId);
        return ResponseEntity.status(HttpStatus.OK).body(DELETE_SUCCESS);
    }

    // 상품별 이미지 조회 (경로 리스트)
    @GetMapping("/{productId}")
    public ResponseEntity<List<String>> getProductProductDetailImage(@PathVariable("productId") Long productId) {
        List<ProductDetailImage> productDetailImages = productDetailImagesService.getProductDetailImage(productId);

        if (!productDetailImages.isEmpty()) {
            List<String> ImagesPaths = productDetailImages.stream()
                    .map(ProductDetailImage::getImagesPath)
                    .collect(Collectors.toList());
            return ResponseEntity.ok().body(ImagesPaths);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
