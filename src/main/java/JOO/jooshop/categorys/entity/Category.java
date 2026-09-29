package JOO.jooshop.categorys.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
//@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "category")
public class Category {
    @Id
    @SequenceGenerator(
            name = "category_sequence",
            sequenceName = "category_sequence",
            allocationSize = 1
    )

    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "category_sequence"
    )

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "name", unique = true, nullable = false)
    @NotBlank(message = "이름은 필수 필드입니다.")
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent")
    public Category parent;

    @Column(name = "depth", nullable = false)
    private Long depth;

    @OneToMany(mappedBy = "parent")
    public List<Category> children = new ArrayList<>();

    // ===================== 생성자 시작 =======================

    public Category() {
    }

    public Category(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Category(Long categoryId, String name, Long depth, List<Category> children) {
        this.categoryId = categoryId;
        this.name = name;
        this.depth = depth;
        this.children = children;
    }

    // 자식 카테고리 생성
    public Category(Category parent, Long depth, String name) {
        this.parent = parent;
        this.depth = depth;
        this.name = name;
    }

    // 부모 카테고리 생성
    public Category(Long depth, String name) {
        this.depth = depth;
        this.name = name;
    }

    // ===================== 생성자 끝 =======================

    public static Category createCategoryById(Long categoryId) {
        return new Category(categoryId);
    }

    public static Category createChildCategory(Long categoryId, String name, Long depth, List<Category> children) {
        return new Category(categoryId, name, depth, children);
    }

    public String getChildCategoryName(String categoryName) {
        for (Category child : children) {
            if (child.getName().equals(categoryName)) {
                return child.getName();
            }
        }
        return null; // 해당하는 자식 카테고리가 없는 경우 null 반환 또는 다른 처리 방법 선택
    }

    /** ID 기반 생성 */
    public static Category ofId(Long categoryId) {
        Category category = new Category();
        category.categoryId = categoryId;
        return category;
    }

    /** 이름 기반 생성 */
    public static Category ofName(String categoryName) {
        Category category = new Category();
        category.name = categoryName;

        // NOT NULL 컬럼 기본값 세팅
        category.depth = 0L;      // 최상위 기준 (DB/정책에 맞게 0 or 1)
        category.parent = null;   // 최상위 카테고리면 null

        return category;
    }
}

