package JOO.jooshop.thumbnail.controller;

import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.product.model.ProductDetailResponseDto;
import JOO.jooshop.product.service.ProductServiceV1;
import JOO.jooshop.thumbnail.model.ProductThumbnailDto;
import JOO.jooshop.thumbnail.service.ThumbnailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ThumbnailViewController {

    private final JWTUtil jwtUtil;
    private final ProductServiceV1 productService;
    private final ThumbnailService thumbnailService;

    /* 상품 전체 조회 */
    // 상품 목록 화면
    @GetMapping
    public String productList(Model model) {
        List<ProductThumbnailDto> products = thumbnailService.getAllThumbnails();
        model.addAttribute("products", products);
        return "products/productList";
    }

    /* 상품 상세 조회 */
    // 상품 상세 화면
    @GetMapping("/{productId}")
    public String productDetail(@PathVariable Long productId,
                                @CookieValue(name = "accessAuthorization", required = false) String accessTokenWithPrefix,
                                Model model) {

        ProductDetailResponseDto productDetail = productService.productDetail(productId);

        model.addAttribute("product", productDetail);
        model.addAttribute("sizes", productDetail.getSizes());

        String memberId = null;
        try {
            if (accessTokenWithPrefix != null && accessTokenWithPrefix.startsWith("Bearer+")) {
                String accessToken = accessTokenWithPrefix.replace("Bearer+", "");
                if (jwtUtil.validateToken(accessToken)) {
                    memberId = jwtUtil.getMemberId(accessToken);
                }
            }
        } catch (Exception e) {
            log.warn("JWT 파싱 실패", e);
        }

        model.addAttribute("memberId", memberId);

        return "products/productDetail";
    }
}
