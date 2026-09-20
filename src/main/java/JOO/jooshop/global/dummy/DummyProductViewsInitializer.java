package JOO.jooshop.global.dummy;

import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.product.service.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * 메인 페이지 "인기 상품 TOP N" 섹션이 빈 화면으로 뜨지 않도록,
 * 아직 조회수가 하나도 없는 더미 상품에게만 최초 1회 랜덤 조회수를 심어주는 초기화 컴포넌트.
 *
 * [배경]
 * 조회수 랭킹(ProductRankingService)은 Redis ZSet에 실제 상품 조회 이벤트가 쌓여야만 값이 생긴다.
 * 서버를 막 띄운 직후에는 이 ZSet이 비어있어서, 인기 상품 섹션이 아무것도 못 보여준다.
 *
 * [운영/로컬 공통 + 실제 트래픽 보존]
 * 예전엔 재기동마다 전체 조회수를 초기화(resetViews)하고 다시 심었지만, 운영에도 이 로직을
 * 적용하면서 그 방식은 위험해졌다 — 실제 사용자가 쌓아둔 조회수까지 재기동할 때마다 날아가게
 * 되기 때문. 그래서 "이 상품에 조회수가 아직 하나도 없으면(=0) 베이스라인만 심어주고,
 * 이미 뭔가 쌓여있으면(더미 시드 완료 or 실제 트래픽) 절대 건드리지 않는다"로 변경.
 *
 * [실행 순서]
 * DummyProductInitializer(@Order(1))가 먼저 더미 상품을 생성해야 productId가 존재하므로,
 * 이 클래스는 반드시 그 다음(@Order(2))에 실행되어야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class DummyProductViewsInitializer implements CommandLineRunner {

    private static final long MIN_VIEWS = 5L;
    private static final long MAX_VIEWS = 300L;

    private final ProductRepository productRepository;
    private final ProductRankingService productRankingService;

    private final Random random = new Random();

    @Override
    public void run(String... args) {
        log.info("[DummyProductViewsInitializer] START");

        List<Long> dummyIds = productRepository.findDummyIds();
        if (dummyIds == null || dummyIds.isEmpty()) {
            log.info("[DummyProductViewsInitializer] no dummy products to seed views for");
            return;
        }

        int seededCount = 0;
        for (Long productId : dummyIds) {
            if (productRankingService.getProductViewCount(productId) > 0) {
                continue; // 이미 조회수 있음(시드 완료됐거나 실제 트래픽 발생) — 건드리지 않음
            }
            long views = MIN_VIEWS + random.nextInt((int) (MAX_VIEWS - MIN_VIEWS + 1));
            productRankingService.seedViewCount(productId, views);
            seededCount++;
        }

        log.info("[DummyProductViewsInitializer] seeded baseline views for {} products (already had views: {})",
                seededCount, dummyIds.size() - seededCount);
        log.info("[DummyProductViewsInitializer] END");
    }
}
