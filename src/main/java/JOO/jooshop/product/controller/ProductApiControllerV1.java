package JOO.jooshop.product.controller;

import JOO.jooshop.global.queries.Condition;
import JOO.jooshop.global.queries.OrderBy;
import JOO.jooshop.product.model.*;
import JOO.jooshop.product.service.ProductOrderService;
import JOO.jooshop.product.service.ProductRankingService;
import JOO.jooshop.product.service.ProductServiceV1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.lang.Nullable;
import java.util.List;

import static JOO.jooshop.global.exception.ResponseMessageConstants.DELETE_SUCCESS;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProductApiControllerV1 {

    private final ObjectMapper objectMapper;
    private final ProductServiceV1 productService;
    private final ProductOrderService productOrderService;

    /**
     * 상품 등록
     *
     * 기존 문제점:
     *  - @RequestBody + MultipartFile 조합은 multipart/form-data에서 동작하지 않음.
     *  - Postman에서 JSON + 파일 동시 전송 불가.
     *
     *  [리팩토링 포인트]
     *  - @RequestPart("requestDto") String 형태로 JSON 문자열 받음.
     *  - ObjectMapper를 사용해 문자열 → DTO 직접 변환 (명시적이고 유연함)
     *  - 이미지 파일은 별도의 @RequestPart로 처리하여 확장성 확보.
     *  - ResponseEntity.status(HttpStatus.CREATED)로 명확한 상태 코드 반환.
     */
    @PostMapping("/products/new")
    public ResponseEntity<String> createProduct(
            @Valid @RequestPart("requestDto") String requestDtoStr,
            @Nullable @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @Nullable @RequestPart(value = "contentImages", required = false) List<MultipartFile> contentImages
    ) throws JsonProcessingException {

        ProductRequestDto requestDto = objectMapper.readValue(requestDtoStr, ProductRequestDto.class);
        Long productId = productService.createProduct(requestDto, thumbnail, contentImages);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("상품 등록 완료. Id : " + productId);
    }

    /**
     * 상품 상세 조회
     *
     *  [리팩토링 포인트]
     *  - 단순히 Product 엔티티를 반환하지 않고, ProductDetailResponseDto로 변환 후 응답.
     *  - 서비스 계층에서 변환 책임을 맡기며, Controller는 전달 역할만 수행.
     *  - 코드 간결화 및 응답 데이터 일관성 향상.
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductDetailResponseDto> getProductById(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.productDetail(productId));
    }

    /**
     * 상품 목록 (카테고리/조건별 필터링, 조건별 정렬, 검색 통합)
     *
     *  [리팩토링 유지]
     *  - 이 부분은 그대로 유지 (상품 목록 조회 로직은 이미 잘 분리되어 있음)
     *  - productOrderService에 위임하여 Controller는 API 엔드포인트 역할만 담당.
     *  - 페이징(Page), 정렬(OrderBy), 조건(Condition), 검색(keyword)을 모두 통합 지원.
     */
    @GetMapping("/products")
    public Page<ProductListResponseDto> getFilteredAndSortedProducts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "condition", required = false) Condition condition,
            @RequestParam(name = "category", required = false) Long category,
            @RequestParam(name = "order", required = false) OrderBy order,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        return productOrderService.getFilteredAndSortedProducts(page, size, condition, order, category, keyword);
    }

    /**
     * 상품 전체 목록
     *
     * [리팩토링 포인트]
     * - 반환 타입을 ResponseEntity<List<ProductDetailResponseDto>>로 통일하여, HTTP 상태 코드
     * - Controller에서 List 직접 반환 대신 ResponseEntity 사용 -> REST 응답 일관성
     */
    @GetMapping("/products/all")
    public ResponseEntity<List<ProductListResponseDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    /**
     * 상품 수정
     *
     * 기존 문제점:
     *  - ProductSummaryResponseDto 를 직접 생성하며, 서비스/컨트롤러 간 DTO 불일치 발생.
     *
     *  [리팩토링 포인트]
     *  - updateProduct() 반환 타입을 ProductDetailResponseDto로 변경 → 단일 DTO 구조 유지.
     *  - Controller에서 별도의 변환 코드 제거 → 중복 로직 최소화.
     *  - 응답 DTO 일관성 유지 (등록/수정 모두 동일한 구조)
     */
    /**
     * 상품 수정
     *
     * @RequestBody + MultipartFile 혼용은 multipart/form-data에서 동작하지 않음.
     * createProduct()와 동일하게 @RequestPart 방식으로 통일.
     */
    @PutMapping("/products/{productId}")
    public ResponseEntity<ProductDetailResponseDto> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestPart("requestDto") String requestDtoStr,
            @Nullable @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            @Nullable @RequestPart(value = "contentImages", required = false) List<MultipartFile> contentImages
    ) throws com.fasterxml.jackson.core.JsonProcessingException {
        ProductRequestDto request = objectMapper.readValue(requestDtoStr, ProductRequestDto.class);
        ProductDetailResponseDto updated = productService.updateProduct(productId, request, thumbnail, contentImages);
        return ResponseEntity.ok(updated);
    }

    /**
     * 상품 삭제
     *
     *  [리팩토링 포인트]
     *  - 예외 발생 시 ControllerAdvice에서 일괄 처리하도록 단순화 가능.
     *  - DELETE_SUCCESS 상수 활용 → 응답 메시지 일관성 강화.
     */
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<String> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.ok(DELETE_SUCCESS);
    }

    /**
     * 색상 등록
     *
     * 기존 문제점:
     *  - 중복 색상 등록 시 내부 서버 오류(500)로 반환되어, 클라이언트 단에서 원인 파악 어려움.
     *
     *  [리팩토링 포인트]
     *  - 예외 발생 시 명시적으로 BAD_REQUEST 반환 → REST 규약에 부합.
     *  - 상태 코드와 메시지의 의미를 명확히 구분하여 API 신뢰도 향상.
     */
    @PostMapping("/color/new")
    public ResponseEntity<String> createColor(@Valid @RequestBody ProductColorDto request) {
        try {
            Long createdColorId = productService.createColor(request);
            return ResponseEntity.status(HttpStatus.CREATED).body("색상 등록 완료 " + createdColorId);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("중복된 이름으로 색상을 등록할 수 없습니다.");
        }
    }

    /**
     * 색상 삭제
     *
     *  [리팩토링 포인트]
     *  - DELETE 응답 시 ResponseEntity.ok(DELETE_SUCCESS) 사용으로 일관성 강화.
     *  - 중복 로직 제거 및 단일 응답 포맷 유지.
     */
    @DeleteMapping("/color/{colorId}")
    public ResponseEntity<String> deleteColor(@PathVariable Long colorId) {
        productService.deleteColor(colorId);
        return ResponseEntity.ok(DELETE_SUCCESS);
    }
}













