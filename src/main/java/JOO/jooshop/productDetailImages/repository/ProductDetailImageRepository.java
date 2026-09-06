package JOO.jooshop.productDetailImages.repository;

import JOO.jooshop.productDetailImages.entity.ProductDetailImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductDetailImageRepository extends JpaRepository<ProductDetailImage, Long> {
    List<ProductDetailImage> findByProduct_ProductId(Long productId);
}
