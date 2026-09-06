package JOO.jooshop.product.repository;

import JOO.jooshop.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * ✅ 상품 목록 조회 (썸네일 같이)
     * - findAll() 오버라이드는 좋긴 한데, 전역적으로 fetch가 걸려서
     *   관리자/배치/다른 기능에서도 무조건 썸네일을 끌고 오게 됨.
     * - 그래서 "의도 드러내는 별도 메서드"로 분리 추천.
     */
    @EntityGraph(attributePaths = {"productThumbnails"})
    @Query("select p from Product p")
    List<Product> findAllWithThumbnails();

    /**
     * ✅ 단건 조회
     * - JpaRepository 기본 findById()가 이미 있으니 굳이 productId 컬럼이 PK라면 findById 쓰는 게 깔끔.
     * - 그래도 너가 productId 네이밍을 유지하고 싶으면 이 메서드 OK.
     */
    Optional<Product> findByProductId(Long productId);

    /**
     * ✅ 더미 상품 id 조회 (오타 수정)
     * - 기존: "select p.idform" 오타 + 필드명도 productId인지 id인지 애매함
     * - Product PK가 productId라면 p.productId가 맞음
     */
    @Query("select p.productId from Product p where p.dummy = true")
    List<Long> findDummyIds();

    /**
     * ✅ 더미 삭제는 굳이 커스텀 delete 메서드 만들 필요 없음
     * - resetDummyData()에서 deleteAllByIdInBatch(ids) 쓰면 끝.
     * - 따라서 아래의 잘못된 메서드는 제거해야 함.
     *
     * ❌ void deleteByProductId(List<Long> productIds);
     */

    /**
     * ✅ 상세 조회 (썸네일/옵션/위시리스트 함께)
     */
    @EntityGraph(attributePaths = {"productThumbnails", "productVariants", "wishLists"})
    Optional<Product> findProductWithDetailsByProductId(Long productId);
}
