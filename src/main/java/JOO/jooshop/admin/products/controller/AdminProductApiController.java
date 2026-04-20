package JOO.jooshop.admin.products.controller;

import JOO.jooshop.admin.products.model.AdminProductRequestDto;
import JOO.jooshop.admin.products.model.AdminProductResponseDto;
import JOO.jooshop.admin.products.service.AdminProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductApiController {

    private final AdminProductService productService;

    @GetMapping
    public ResponseEntity<List<AdminProductResponseDto>> getAllProducts() {
        return ResponseEntity.ok(productService.findAllProduct());
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<AdminProductResponseDto> createProduct(
            @ModelAttribute AdminProductRequestDto dto,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "contentImages", required = false) List<MultipartFile> contentImages
    ) {
        return ResponseEntity.ok(productService.createProduct(dto, thumbnail, contentImages));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<AdminProductResponseDto> updateProduct(
            @PathVariable Long id,
            @ModelAttribute AdminProductRequestDto dto,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "contentImages", required = false) List<MultipartFile> contentImages
    ) {
        return ResponseEntity.ok(productService.updateProduct(id, dto, thumbnail, contentImages));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}