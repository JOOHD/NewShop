package JOO.jooshop.product.repository;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.enums.ProductType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 목록 N+1 전/후 쿼리 수 비교.
 *
 * 상품 10개(각각 썸네일 1개)를 저장한 뒤 "목록 조회 + 각 상품의 썸네일 접근"을 하면서
 * Hibernate가 DB로 보낸 SQL(PreparedStatement) 개수를 센다.
 *
 *  - Before: findAll()                  → 상품 1번 + 상품마다 썸네일 N번 = N+1
 *  - After : findAllWithThumbnails()    → @EntityGraph로 썸네일을 함께 조회 = 1번
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase
class ProductQueryCountTest {

    private static final int PRODUCT_COUNT = 10;

    @Autowired ProductRepository productRepository;
    @PersistenceContext EntityManager em;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Product product = Product.createDemo("N+1 테스트 상품 " + i, ProductType.values()[0],
                    BigDecimal.valueOf(10_000), "info", "maker", false, 0, false);
            product.addThumbnailPath("/images/demo/test" + i + ".jpg");
            productRepository.save(product);
        }
        em.flush();
        em.clear(); // 1차 캐시를 비워야 이후 조회가 실제로 DB로 나간다

        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("[Before] findAll() 후 썸네일 접근 — 상품 수만큼 쿼리가 추가로 나간다 (N+1)")
    void findAll_thenAccessThumbnails_nPlusOne() {
        statistics.clear();

        List<Product> products = productRepository.findAll();
        products.forEach(p -> p.getProductThumbnails().size()); // 썸네일 접근 → 상품마다 쿼리

        long queries = statistics.getPrepareStatementCount();
        System.out.printf("[N+1 Before] 상품 %d개 → 쿼리 %d번%n", PRODUCT_COUNT, queries);

        assertThat(queries).isEqualTo(PRODUCT_COUNT + 1);
    }

    @Test
    @DisplayName("[After] findAllWithThumbnails() — 썸네일을 함께 조회해 쿼리 1번")
    void findAllWithThumbnails_singleQuery() {
        statistics.clear();

        List<Product> products = productRepository.findAllWithThumbnails();
        products.forEach(p -> p.getProductThumbnails().size());

        long queries = statistics.getPrepareStatementCount();
        System.out.printf("[N+1 After] 상품 %d개 → 쿼리 %d번%n", PRODUCT_COUNT, queries);

        assertThat(queries).isEqualTo(1);
    }
}
