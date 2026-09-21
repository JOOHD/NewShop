package JOO.jooshop.global.dummy;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.product.service.ProductRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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
 *
 * [메인 배너 연동 — 2026-09]
 * 메인 히어로 배너 슬라이드 1(adidas x Man Utd 트레이닝룩), 슬라이드 2(adidas x Man Utd x
 * The Stone Roses 홈 저지)와 테마가 맞닿아 있는 상품이 TOP5에 확실히 노출되도록, 해당 두 상품은
 * 다른 더미 상품보다 압도적으로 높은 조회수를 부여한다. 현재 조회수는 어차피 전부 의미 없는
 * 랜덤 더미값이라 교체해도 무방 — 실제 트래픽이 쌓이기 시작하면 이 초기값은 자연히 묻힌다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class DummyProductViewsInitializer implements CommandLineRunner {

    private static final long MIN_VIEWS = 5L;
    private static final long MAX_VIEWS = 300L;

    // 메인 배너 테마 연동 상품 — 이름으로 매칭해서 TOP5 최상위로 밀어줌
    private static final Map<String, Long> FEATURED_PRODUCT_VIEWS = Map.of(
            "2025 맨유 트레이닝 웨어", 9000L, // 슬라이드 1: adidas x Man Utd 트레이닝룩 배너
            "2025 맨유 홈 저지", 8999L        // 슬라이드 2: adidas x Man Utd x The Stone Roses 홈 저지 배너
    );

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

        List<Product> dummyProducts = productRepository.findAllById(dummyIds);

        int seededCount = 0;
        for (Product product : dummyProducts) {
            Long productId = product.getProductId();
            if (productRankingService.getProductViewCount(productId) > 0) {
                continue; // 이미 조회수 있음(시드 완료됐거나 실제 트래픽 발생) — 건드리지 않음
            }

            Long featuredViews = FEATURED_PRODUCT_VIEWS.get(product.getProductName());
            long views = (featuredViews != null)
                    ? featuredViews
                    : MIN_VIEWS + random.nextInt((int) (MAX_VIEWS - MIN_VIEWS + 1));

            productRankingService.seedViewCount(productId, views);
            seededCount++;
        }

        log.info("[DummyProductViewsInitializer] seeded baseline views for {} products (already had views: {})",
                seededCount, dummyIds.size() - seededCount);
        log.info("[DummyProductViewsInitializer] END");
    }
}
