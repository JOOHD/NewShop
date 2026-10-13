package JOO.jooshop.productVariant.repository;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.repository.CategoryRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.product.repository.ProductColorRepository;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.productVariant.entity.ProductVariant;
import JOO.jooshop.productVariant.entity.enums.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 동시성 테스트 — 재고 10개인 옵션에 50개 스레드가 동시에 1개씩 주문한다.
 *
 * 기대 결과: 성공 정확히 10건, 실패 40건, 최종 재고 0 (음수 재고/초과 판매 없음)
 *
 * 참고: 같은 시나리오를 "조회 → 차감 → 저장"(읽고 쓰기) 방식으로 짜면 여러 스레드가 같은 재고 값을 읽어
 *       초과 판매(성공 > 10)가 발생한다. 조건부 UPDATE로 바꾼 뒤 결과를 PROJECT_OVERVIEW/ONE_PAGER에 기록.
 */
@DataJpaTest
@AutoConfigureTestDatabase // 인메모리 H2로 교체 (운영 MySQL 설정은 사용하지 않음)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // 스레드마다 별도 트랜잭션이 필요해서 테스트 전체 트랜잭션을 끈다
class StockConcurrencyTest {

    private static final int STOCK = 10;
    private static final int THREADS = 50;

    @Autowired ProductVariantRepository variantRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductColorRepository colorRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlatformTransactionManager txManager;

    @Test
    @DisplayName("재고 10개에 50명이 동시에 1개씩 주문하면 정확히 10명만 성공한다")
    void decreaseStock_concurrent_noOversell() throws Exception {
        // given
        Long variantId = createVariantWithStock("AFTER");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        // when — 모든 스레드가 준비된 뒤 동시에 출발
        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    int updated = variantRepository.decreaseStockIfEnough(variantId, 1L);
                    if (updated == 1) success.incrementAndGet(); else fail.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        // 타임아웃: 데드락 등으로 끝나지 않아도 테스트가 영원히 멈추지 않게 10초 후 실패 처리
        assertThat(done.await(10, TimeUnit.SECONDS)).as("모든 스레드가 10초 안에 끝나야 함").isTrue();
        pool.shutdown();

        // then
        ProductVariant result = variantRepository.findById(variantId).orElseThrow();
        System.out.printf("[동시성 결과] 성공=%d, 실패=%d, 최종재고=%d%n", success.get(), fail.get(), result.getProductStock());

        assertThat(success.get()).isEqualTo(STOCK);
        assertThat(fail.get()).isEqualTo(THREADS - STOCK);
        assertThat(result.getProductStock()).isZero();
    }

    // 재고 STOCK개인 옵션 하나를 만들고 옵션 ID를 반환 (테스트마다 이름 접두어를 달리해 데이터 충돌 방지)
    private Long createVariantWithStock(String prefix) {
        ProductColor color = colorRepository.save(ProductColor.ofName(prefix + "_COLOR"));
        Category category = categoryRepository.save(Category.ofName(prefix + "_CATEGORY"));
        Product product = Product.createDemo(prefix + " 동시성 테스트 상품", ProductType.values()[0],
                BigDecimal.valueOf(10_000), "info", "maker", false, 0, false);
        product.addOption(color, category, Gender.values()[0], Size.values()[0], STOCK);
        Product saved = productRepository.save(product);
        // 방금 저장한 상품의 옵션 ID를 직접 꺼낸다 (findAll().get(0)은 다른 데이터가 있으면 엉뚱한 옵션을 집을 수 있음)
        return saved.optionsView().get(0).getVariantId();
    }

    /**
     * [적용 전 비교용] "조회 → 차감 → 저장"(읽고 쓰기) 방식으로 같은 50명이 동시에 주문.
     * 락도 조건부 UPDATE도 없어서, 여러 스레드가 같은 재고 값을 읽고 덮어써 초과 판매가 생길 수 있다.
     * 결과가 매번 달라질 수 있어 assert 없이 출력만 한다 — 이 숫자가 문서의 "적용 전" 수치.
     */
    @Test
    @DisplayName("[비교용] 읽고 쓰기 방식은 동시에 주문하면 초과 판매가 생긴다")
    void decreaseStock_readThenWrite_oversellDemo() throws Exception {
        Long variantId = createVariantWithStock("BEFORE");
        TransactionTemplate tx = new TransactionTemplate(txManager);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    tx.executeWithoutResult(status -> {
                        ProductVariant v = variantRepository.findById(variantId).orElseThrow(); // 조회
                        v.decreaseStock(1L);                                                     // 메모리에서 차감 (재고 부족이면 예외)
                        variantRepository.saveAndFlush(v);                                       // 저장
                    });
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 재고 부족 예외 또는 충돌 — 실패로 간주
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        long remaining = variantRepository.findById(variantId).orElseThrow().getProductStock();
        System.out.printf("[적용 전 결과] 성공=%d (재고는 %d개뿐), 최종재고=%d%n", success.get(), STOCK, remaining);
    }
}
