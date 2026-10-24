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
 * default_batch_fetch_size 적용 효과 측정.
 *
 * ProductQueryCountTest의 [Before]는 batch size를 1로 꺼서 순수 N+1(11번)을 보여 준다.
 * 이 테스트는 운영 설정(1000)처럼 묶음 조회를 켠 상태에서 같은 코드(findAll + 썸네일 접근)가
 * 몇 번의 쿼리로 줄어드는지 확인한다. 정확한 숫자는 실행 결과의 출력 줄을 기록한다.
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.default_batch_fetch_size=1000"
})
@AutoConfigureTestDatabase
class ProductBatchFetchTest {

    private static final int PRODUCT_COUNT = 10;

    @Autowired ProductRepository productRepository;
    @PersistenceContext EntityManager em;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < PRODUCT_COUNT; i++) {
            Product product = Product.createDemo("배치 테스트 상품 " + i, ProductType.values()[0],
                    BigDecimal.valueOf(10_000), "info", "maker", false, 0, false);
            product.addThumbnailPath("/images/demo/batch" + i + ".jpg");
            productRepository.save(product);
        }
        em.flush();
        em.clear();

        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("[Batch] findAll() 후 썸네일 접근 — 묶음 조회로 N+1보다 적은 쿼리")
    void findAll_thenAccessThumbnails_batchFetch() {
        statistics.clear();

        List<Product> products = productRepository.findAll();
        products.forEach(p -> p.getProductThumbnails().size());

        long queries = statistics.getPrepareStatementCount();
        System.out.printf("[Batch Fetch] 상품 %d개 → 쿼리 %d번%n", PRODUCT_COUNT, queries);

        assertThat(queries).isLessThan(PRODUCT_COUNT + 1);
    }
}
