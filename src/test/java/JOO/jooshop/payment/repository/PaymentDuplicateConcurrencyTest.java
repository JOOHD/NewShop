package JOO.jooshop.payment.repository;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.repository.CategoryRepository;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.order.entity.OrderProduct;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.entity.enums.PayMethod;
import JOO.jooshop.order.repository.OrderRepository;
import JOO.jooshop.payment.entity.PaymentHistory;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 결제번호(imp_uid)가 동시에 여러 번 들어와도 결제 이력이 한 번만 저장되는지 확인한다.
 *
 * PaymentService.processPaymentDone 의 핵심 흐름을 그대로 옮긴 시나리오다.
 *   주문 조회 → "이미 처리된 imp_uid인가?" 조회 → 아니면 결제 이력 저장
 * 조회와 저장 사이에 다른 요청이 끼어들 수 있으므로, 주문 행을 잠그고(findByIdForUpdate) 시작하면
 * 같은 주문의 요청이 한 번에 하나씩만 이 구간을 지나가 중복 저장이 사라진다.
 *
 * - 잠금 사용: 이력이 정확히 1건만 저장돼야 한다 (assert)
 * - 잠금 미사용: 비교용 — 여러 건이 저장될 수 있어 결과를 출력만 한다
 */
@DataJpaTest
@AutoConfigureTestDatabase
@Transactional(propagation = Propagation.NOT_SUPPORTED) // 스레드마다 별도 트랜잭션이 필요해서 테스트 전체 트랜잭션을 끈다
class PaymentDuplicateConcurrencyTest {

    private static final int THREADS = 5;

    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductColorRepository colorRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired PlatformTransactionManager txManager;

    @Test
    @DisplayName("주문 행을 잠그고 처리하면 같은 imp_uid가 동시에 와도 결제 이력은 1건만 저장된다")
    void duplicateImpUid_withLock_savedOnce() throws Exception {
        long saved = runConcurrently(true);
        System.out.printf("[결제 중복 방어 - 잠금 사용] 동시 요청 %d개 → 저장된 결제 이력 %d건%n", THREADS, saved);

        assertThat(saved).isEqualTo(1);
    }

    @Test
    @DisplayName("[비교용] 잠금 없이 조회 후 저장하면 같은 imp_uid가 여러 건 저장될 수 있다")
    void duplicateImpUid_withoutLock_demo() throws Exception {
        long saved = runConcurrently(false);
        System.out.printf("[결제 중복 방어 - 잠금 미사용] 동시 요청 %d개 → 저장된 결제 이력 %d건%n", THREADS, saved);
        // 타이밍에 따라 결과가 달라질 수 있어 assert 없이 출력만 한다 (잠금 사용 결과와 비교하는 용도)
    }

    // 같은 주문·같은 imp_uid로 THREADS개 요청을 동시에 처리하고, 저장된 결제 이력 수를 반환
    private long runConcurrently(boolean useLock) throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String impUid = "imp_" + tag;
        Long orderId = createOrder(tag);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    tx.executeWithoutResult(status -> processPayment(orderId, impUid, useLock));
                } catch (Exception ignored) {
                    // 잠금 대기 시간 초과 등으로 실패한 요청은 저장되지 않으므로 결과 수에 영향 없음
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).as("모든 스레드가 20초 안에 끝나야 함").isTrue();
        pool.shutdown();

        return paymentRepository.findAll().stream()
                .filter(p -> impUid.equals(p.getImpUid()))
                .count();
    }

    // processPaymentDone 의 "조회 → 중복 확인 → 저장" 구간을 옮긴 것
    private void processPayment(Long orderId, String impUid, boolean useLock) {
        Orders order = (useLock
                ? orderRepository.findByIdForUpdate(orderId)
                : orderRepository.findById(orderId)).orElseThrow();

        if (paymentRepository.existsByImpUid(impUid)) {
            return; // 이미 처리된 결제
        }

        sleepBriefly(); // 조회와 저장 사이의 틈을 넓혀 경쟁 상황을 재현

        for (OrderProduct orderProduct : order.getOrderProducts()) {
            paymentRepository.save(PaymentHistory.createPaymentHistory(
                    order.getMember(), order, orderProduct, impUid, "card",
                    order.getTotalPrice(), null, null, "서울", "test@test.com"));
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 회원 1명, 상품 옵션 1개, 주문상품 1개가 달린 주문을 만들고 주문 ID를 반환
    private Long createOrder(String tag) {
        Member member = memberRepository.save(Member.registerGeneral(
                tag + "@example.com", "encodedPw", "홍길동", "닉" + tag, "010-1234-5678", "social-" + tag));

        ProductColor color = colorRepository.save(ProductColor.ofName(tag + "_COLOR"));
        Category category = categoryRepository.save(Category.ofName(tag + "_CATEGORY"));
        Product product = Product.createDemo(tag + " 결제 동시성 테스트 상품", ProductType.values()[0],
                BigDecimal.valueOf(10_000), "info", "maker", false, 0, false);
        product.addOption(color, category, Gender.values()[0], Size.values()[0], 10);
        Product savedProduct = productRepository.save(product);
        ProductVariant variant = savedProduct.optionsView().get(0);

        Orders order = Orders.createOrder(member, "홍길동", "010-1234-5678", "12345",
                "서울시 강남구", null, PayMethod.card, "order_" + tag);
        order.addOrderProduct(OrderProduct.createOrderProduct(
                variant, "테스트 상품", "M", null, BigDecimal.valueOf(10_000), 1));
        return orderRepository.save(order).getOrderId();
    }
}
