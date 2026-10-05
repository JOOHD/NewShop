package JOO.jooshop.admin.products.controller;

import JOO.jooshop.admin.products.model.AdminProductRequestDto;
import JOO.jooshop.admin.products.model.AdminProductResponseDto;
import JOO.jooshop.admin.products.service.AdminProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 상품", description = "관리자 전용 상품 등록/수정/삭제")
@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductApiController {

    private final AdminProductService productService;

    // 관리자 상품 전체 목록 조회
    @GetMapping
    public ResponseEntity<List<AdminProductResponseDto>> getAllProducts() {
        return ResponseEntity.ok(productService.findAllProduct());
    }

    // 관리자 상품 등록
    @PostMapping
    public ResponseEntity<AdminProductResponseDto> createProduct(@RequestBody AdminProductRequestDto dto) {
        return ResponseEntity.ok(productService.createProduct(dto));
    }

    // 관리자 상품 수정
    @PutMapping("/{id}")
    public ResponseEntity<AdminProductResponseDto> updateProduct(
            @PathVariable Long id,
            @RequestBody AdminProductRequestDto dto
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, dto));
    }

    // 관리자 상품 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}