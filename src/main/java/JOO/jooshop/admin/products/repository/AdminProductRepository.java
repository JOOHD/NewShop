package JOO.jooshop.admin.products.repository;

import JOO.jooshop.product.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"productThumbnails"})
    // @Query("select p from Product p")
    List<Product> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {
            "productThumbnails",
            "productDetailImages",
            "productVariants",
            "productVariants.color",
            "productVariants.category"
    })
    // 스프링 메서드 이름만으로는 안 먹을 수 있어서, @Query 붙이는게 안전
    @Query("select p from Product p where p.productId = :productId")
    Optional<Product> findWithDetailsByProductId(Long productId);
}
