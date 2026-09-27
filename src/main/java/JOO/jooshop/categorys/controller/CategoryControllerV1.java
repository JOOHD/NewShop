package JOO.jooshop.categorys.controller;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.model.CategoryDto;
import JOO.jooshop.categorys.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static JOO.jooshop.global.exception.ResponseMessageConstants.DELETE_SUCCESS;

@Tag(name = "카테고리", description = "상품 카테고리 조회/등록/수정/삭제")
@RestController
@RequestMapping("/api/v1/categorys")
@RequiredArgsConstructor
public class CategoryControllerV1 {

    private final CategoryService categoryService;

    /**
     * 전체 카테고리 조회
     * 자식 카테고리까지 findAll로 한 번에 가져오면 중복 노출되는 문제가 있어,
     * 최상위 카테고리만 조회하고 자식은 엔티티 연관관계로 함께 내려준다.
     * @return
     */
    @GetMapping("")
    public ResponseEntity<List<CategoryDto>> getCategoryList() {
        List<Category> topLevelCategories = categoryService.getTopLevelCategories(); // 최상위 부모 카테고리만 가져오는 메서드

        // 부모 카테고리 리스트를 DTO로 변환
        List<CategoryDto> categoryDtoList = topLevelCategories.stream()
                .map(CategoryDto::of)
                .collect(Collectors.toList());
        return ResponseEntity.ok(categoryDtoList);
    }

    /**
     * 부모 카테고리 생성
     * @param category
     * @return
     */
    @PostMapping("/parent")
    public ResponseEntity<Long> createParenCategory(@RequestBody Category category) {
        Long categoryId = categoryService.createCategory(category, null); // 부모 카테고리 생성 시 parentId 를 null 로 전달
        return ResponseEntity.ok(categoryId);
    }

    /**
     * 자식 카테고리 생성
     * @param category
     * @param parentId
     * @return
     */
    @PostMapping("/child/{parentId}")
    public ResponseEntity<Long> createChildCategory(@RequestBody Category category, @PathVariable("parentId") Long parentId) {
        Long categoryId = categoryService.createCategory(category, parentId); // 부모 카테고리의 ID를 parentId로 전달하여 자식 카테고리 생성
        return ResponseEntity.ok(categoryId);
    }

    /**
     * 카테고리 삭제
     * @param categoryId
     * @return
     */
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<String> deleteCategory(@PathVariable("categoryId") Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return ResponseEntity.ok(DELETE_SUCCESS);
    }
}
