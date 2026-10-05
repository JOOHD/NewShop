package JOO.jooshop.productVariant.repository;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    // 상품/색상/카테고리/사이즈가 같은 옵션 조회
    Optional<ProductVariant> findByProductAndColorAndCategoryAndSize(
            Product product, ProductColor color, Category category, Size size
    );

    // 상품명 + 사이즈로 옵션 조회 (성별 옵션이 여러 개일 수 있어 목록으로 반환)
    @Query("select v from ProductVariant v where v.product.productName = :productName and v.size = :size")
    List<ProductVariant> findByProductNameAndSize(@Param("productName") String productName, @Param("size") Size size);

    // 카테고리에 속한 옵션 전체 조회
    List<ProductVariant> findByCategory(Category category);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProductVariant pm where pm.product.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProductVariant pm where pm.product.productId in :productIds")
    void deleteByProductIdIn(@Param("productIds") List<Long> productIds);
}
