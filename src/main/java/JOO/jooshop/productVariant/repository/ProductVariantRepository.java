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
import org.springframework.transaction.annotation.Transactional;

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

    // 재고 조건부 차감 — "읽고 → 계산하고 → 쓰는" 대신 DB가 한 문장으로 검사+차감을 처리한다.
    // 동시에 여러 주문이 들어와도 행 잠금으로 순차 처리되고, 재고가 모자라면 0건이 갱신된다(→ 호출부에서 재고 부족 처리).
    @Transactional
    @Modifying(clearAutomatically = false)
    @Query("update ProductVariant v set v.productStock = v.productStock - :qty " +
           "where v.variantId = :variantId and v.productStock >= :qty")
    int decreaseStockIfEnough(@Param("variantId") Long variantId, @Param("qty") long qty);

    // 재고 복구 — 결제되지 않은 주문이 만료·취소될 때 차감했던 수량을 되돌린다.
    // 증가도 "읽고 → 더하고 → 쓰기"가 아니라 DB가 한 문장으로 처리해 동시 요청과 섞여도 값이 틀어지지 않는다.
    // 재고가 다시 생기므로 품절 표시도 해제한다.
    @Transactional
    @Modifying(clearAutomatically = false)
    @Query("update ProductVariant v set v.productStock = v.productStock + :qty, v.soldOut = false " +
           "where v.variantId = :variantId")
    int increaseStock(@Param("variantId") Long variantId, @Param("qty") long qty);

    // 재고가 0이 된 옵션을 품절 처리 (차감 직후 같은 트랜잭션에서 호출)
    @Transactional
    @Modifying(clearAutomatically = false)
    @Query("update ProductVariant v set v.soldOut = true where v.variantId = :variantId and v.productStock = 0")
    int markSoldOutIfEmpty(@Param("variantId") Long variantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProductVariant pm where pm.product.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProductVariant pm where pm.product.productId in :productIds")
    void deleteByProductIdIn(@Param("productIds") List<Long> productIds);
}
