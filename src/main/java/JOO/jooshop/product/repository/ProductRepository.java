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
     *
     * [2026-09-28 수정] Hibernate는 List(bag) 타입 컬렉션 2개 이상을 한 EntityGraph에서
     * 동시에 fetch join 하는 것을 금지한다(MultipleBagFetchException) — 여러 컬렉션을 동시에
     * join하면 카테시안 곱으로 행이 뻥튀기되는데, List는 이 중복을 걸러낼 방법(정렬 키)이 없어서
     * Hibernate가 아예 쿼리 생성 단계에서 막아버린다. 기존 코드는 productThumbnails/productVariants/
     * wishLists 세 개의 List를 한 번에 fetch 하려다 상품 상세 페이지가 전부 500(400)으로 막혀있었음.
     *
     * 상세 조회는 상품 1건만 대상이라 N+1이 발생해도 실제로는 "쿼리 1개(본문) + 지연로딩 쿼리 2~3개"
     * 수준이라 성능에 영향이 없다. 그래서 fetch join은 가장 먼저 화면에 필요한 productThumbnails만
     * 남기고, productVariants/wishLists는 지연 로딩에 맡긴다(호출부가 @Transactional 안에서 도니
     * LazyInitializationException 걱정 없음).
     */
    @EntityGraph(attributePaths = {"productThumbnails"})
    Optional<Product> findProductWithDetailsByProductId(Long productId);
}
