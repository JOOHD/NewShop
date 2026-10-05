package JOO.jooshop.thumbnail.controller;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.repository.CategoryRepository;
import JOO.jooshop.categorys.service.CategoryService;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.product.model.ProductDetailResponseDto;
import JOO.jooshop.product.model.ProductListResponseDto;
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
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;

    /* 상품 전체 조회 */
    // 상품 목록 화면 — ?category=ID 로 카테고리(하위 포함), ?collab=true 로 콜라보 상품만 필터링
    @GetMapping
    public String productList(@RequestParam(name = "category", required = false) Long categoryId,
                              @RequestParam(name = "collab", defaultValue = "false") boolean collab,
                              Model model) {
        List<ProductListResponseDto> products = productService.getProducts(categoryId, collab);

        // 화면 제목 + 상단 카테고리 칩(선택된 카테고리의 최상위 기준으로 하위 카테고리 표시)
        String title = "전체 상품";
        Long selectedTopId = null;
        if (collab) {
            title = "콜라보";
        } else if (categoryId != null) {
            Category selected = categoryRepository.findByCategoryId(categoryId).orElse(null);
            if (selected != null) {
                title = selected.getName();
                selectedTopId = selected.getParent() != null ? selected.getParent().getCategoryId() : selected.getCategoryId();
            }
        }

        model.addAttribute("products", products);
        model.addAttribute("categories", categoryService.getTopLevelCategories());
        model.addAttribute("title", title);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedTopId", selectedTopId);
        model.addAttribute("collab", collab);
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
