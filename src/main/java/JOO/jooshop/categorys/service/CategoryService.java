package JOO.jooshop.categorys.service;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.model.CategoryDto;
import JOO.jooshop.categorys.repository.CategoryRepository;
import JOO.jooshop.global.authorization.RequiresRole;
import JOO.jooshop.members.entity.enums.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * 전체 카테고리 조회
     * @return
     */
    public List<Category> getTopLevelCategories() {
        return categoryRepository.findByParentIsNull(); // 부모 카테고리가 null 인 경우
    }
    
    // 카테고리 전체 목록 조회
    public List<CategoryDto> getCategoryList() {
        return categoryRepository.findAll().stream()
                .map(CategoryDto::of)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리 생성
     * parentId 파라미터가 없는 경우 - 부모 카테고리를 만든다.
     * 있는 경우 - 해당하는 부모 카테고리 밑에 자식 카테고리를 만든다.
     */
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public Long createCategory(Category request, Long parentId) {
        if (parentId != null) {
            // 부모 카테고리가 지정된 경우
            Category parentCategory = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new NoSuchElementException("해당 카테고리를 찾을 수 없습니다."));

            if (parentCategory.getDepth() != 0L) {
                // 입력한 parentId 가 상위 카테고리의 id 가 아닌 하위 카테고리의 id 일 경우 에러 반환
                throw new InvalidParameterException("올바른 상위 카테고리를 입력하세요");
            }

            Category category = new Category(parentCategory, parentCategory.getDepth() + 1, request.getName());
            parentCategory.getChildren().add(category);
            return categoryRepository.save(category).getCategoryId();
        }

        // 부모 카테고리가 지정되지 않은 경우
        Category childCategory = new Category(0l, request.getName());
        return categoryRepository.save(childCategory).getCategoryId();
    }

    // 카테고리 삭제 (하위 카테고리가 있으면 삭제 불가)
    @RequiresRole({MemberRole.ADMIN, MemberRole.SELLER})
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new NoSuchElementException("해당 카테고리를 찾을 수 없습니다. Id : " + categoryId));

        if (category.getChildren().isEmpty()) {
            categoryRepository.delete(category);
        } else {
            throw new IllegalArgumentException("삭제 실패 : 하위 카테고리가 존재합니다.");
        }
    }
}

