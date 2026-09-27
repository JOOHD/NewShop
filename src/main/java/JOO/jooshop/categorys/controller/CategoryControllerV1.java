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

    // 전체 카테고리 조회 - 자식 카테고리가 한 번 더 나오는 문제 때문에 findall 사용하면 에러 발생
    /*
        @GetMapping("/categories")
        public ResponseEntity<?> getCategoryList() {
            return ResponseEntity.ok(productServiceV1.getCategoryList());
        }
     */

    /**
     * 전체 카테고리 조회
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

    /*
        categoryId	categoryName	parentId (FK)
        1	        음료	            null
        2	        커피	            1
        3	        아메리카노	    2

        - "아메리카노"는 categoryId = 3, parentId = 2 인 부모 커피를 가리킴
        - 자기 자신의 고유한 categoryId (PK), 부모 엔티티 참조를 통해서 또 다른 카테고리의 categoryId (FK)
        
        1. 클라이언트가 parent.categoryId 를 포함한 Category 를 보냄
        2. 서버는 parentId 를 꺼내서, 이걸 FK 로 저장하고 자식 카테고리를 저장
        3. 저장하면서 생성된 자식의 categoryId 를 반환
     */
//    @PostMapping("/child") // 부모 카테고리 id를 바디로 보냄
//    public ResponseEntity<Long> createChildCategory(@RequestBody Category category) {
//        Long parentId = category.getParent().getCategoryId(); // 요청 바디에 있는 부모 카테고리의 ID를 가져옵니다.
//        Long categoryId = categoryService.createCategory(category, parentId); // 부모 카테고리의 ID를 parentId로 전달하여 자식 카테고리 생성
//        return ResponseEntity.ok(categoryId);
//    }

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
