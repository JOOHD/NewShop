package JOO.jooshop.admin.products.controller;

import JOO.jooshop.admin.products.model.AdminProductResponseDto;
import JOO.jooshop.admin.products.service.AdminProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductViewController { // 페이지 라우팅

    private final AdminProductService productService;

    /* 상품 목록 */
    // 관리자 상품 목록 화면
    @GetMapping("/list")
    public String productList(Model model) {
        List<AdminProductResponseDto> products = productService.findAllProduct();
        model.addAttribute("products", products);
        return "admin/products/productList";
    }

    /* 상품 등록 */
    // 관리자 상품 등록 화면
    @GetMapping("/register")
    public String productRegister() {
        return "admin/products/productRegister";
    }

    /* 상품 수정 */
    // 관리자 상품 수정 화면
    @GetMapping("/edit/{id}")
    public String productEdit(@PathVariable Long id, Model model) {
        model.addAttribute("productId", id);
        return "admin/product/productEdit";
    }
}
