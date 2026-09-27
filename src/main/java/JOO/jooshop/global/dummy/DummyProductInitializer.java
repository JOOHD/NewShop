package JOO.jooshop.global.dummy;

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
import JOO.jooshop.productVariant.repository.ProductVariantRepository;
import JOO.jooshop.thumbnail.repository.ProductThumbnailRepositoryV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * [운영/로컬 공통 사용]
 * 예전엔 @Profile("local")로 로컬 전용이었으나, 최초 1회만 생성하고 이후엔 건드리지 않는
 * 방식으로 바뀌면서 운영에서 돌려도 안전해져 프로필 제한을 없앰 — 포트폴리오 데모용으로
 * 운영 사이트에도 상품/썸네일이 항상 보이도록 함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // DummyProductViewsInitializer(조회수 시딩)보다 먼저 실행되어야 함 — 상품이 먼저 있어야 조회수를 심을 수 있음
public class DummyProductInitializer implements CommandLineRunner { // 스프링 부트 시작 시, 자동 실행

    private static final String DUMMY_CATEGORY_NAME = "DUMMY";
    private static final String DUMMY_COLOR_NAME = "DUMMY_COLOR";
    private static final long DEFAULT_STOCK = 20L;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository productColorRepository;

    private final ProductThumbnailRepositoryV1 productThumbnailRepository;
    private final ProductVariantRepository productVariantRepository;

    private final Random random = new Random();

    /**
     * [정책 변경 — 운영/로컬 통일]
     * 예전엔 재기동마다 더미 상품을 삭제 후 재생성했지만(resetDummyData()),
     * 이 방식을 운영(EC2)에 그대로 적용하면 더미 상품을 참조하는 주문(OrderProduct FK)이
     * 있을 경우 삭제가 막히거나 부팅이 실패할 위험이 있었다.
     * → "더미 상품이 이미 있으면 손대지 않고, 없을 때만 최초 1회 생성"으로 변경.
     * 로컬/운영 모두 같은 로직을 쓰되, 한 번 생성된 더미 상품은 영구적으로 유지된다.
     * (완전히 새로 시딩하고 싶으면 DB에서 더미 상품을 직접 삭제하고 재기동 — resetDummyData()는
     *  지금은 run()에서 자동 호출되지 않지만, 필요하면 그대로 재사용 가능하도록 남겨둠)
     */
    @Override
    @Transactional
    public void run(String... args) {

        log.info("[DummyProductInitializer] START");

        if (!productRepository.findDummyIds().isEmpty()) {
            log.info("[DummyProductInitializer] dummy products already exist — skip (no reset)");
            return;
        }

        Category dummyCategory = getOrCreateDefaultCategory(); // 기본 카테고리/컬러 확보
        ProductColor dummyColor = getOrCreateDefaultColor();

        createDummyProducts(dummyCategory, dummyColor); // 최초 1회만 생성

        log.info("[DummyProductInitializer] END");
    }

    private Category getOrCreateDefaultCategory() {
        return categoryRepository.findByName(DUMMY_CATEGORY_NAME)
                .orElseGet(() -> categoryRepository.save(Category.ofName(DUMMY_CATEGORY_NAME)));
    }

    private ProductColor getOrCreateDefaultColor() {
        return productColorRepository.findByColor(DUMMY_COLOR_NAME)
                .orElseGet(() -> productColorRepository.save(ProductColor.ofName(DUMMY_COLOR_NAME)));
    }

    /**
     * reset = 기존 더미 데이터만 삭제
     *
     * 전제:
     * - productRepository.findDummyIds() : 더미로 판단되는 product id 리스트 반환
     * - 썸네일/옵션은 FK 때문에 먼저 삭제 후 product 삭제
     *
     * 삭제 전략:
     * 1) bulk delete 메서드 있으면 bulk로
     * 2) 없으면 (레포가 단수만 있으면) 반복 삭제로 fallback
     */
    protected void resetDummyData() {
        log.info("[DummyProductInitializer] delete dummy data only");

        List<Long> dummyIds = productRepository.findDummyIds();
        if (dummyIds == null || dummyIds.isEmpty()) {
            log.info("[DummyProductInitializer] no dummy data to delete");
            return;
        }

        // 1) 옵션(ProductVariant) 먼저 삭제
        safeDeleteOptionsByProductIds(dummyIds);

        // 2) 썸네일 먼저 삭제
        safeDeleteThumbnailsByProductIds(dummyIds);

        // 3) Product 삭제 (batch)
        productRepository.deleteAllByIdInBatch(dummyIds);

        log.info("[DummyProductInitializer] deleted dummy products: {}", dummyIds.size());
    }

    private void safeDeleteOptionsByProductIds(List<Long> productIds) {
        try {
            // ✅ bulk 메서드가 있으면 이걸 쓰는 게 최적
            productVariantRepository.deleteByProductIdIn(productIds);
            log.info("[DummyProductInitializer] deleted options (bulk): {}", productIds.size());
        } catch (Exception bulkFail) {
            // ✅ bulk 메서드가 없거나 실패하면 단수 delete로 fallback
            log.warn("[DummyProductInitializer] bulk delete options failed -> fallback to single delete. size={}",
                    productIds.size(), bulkFail);

            for (Long productId : productIds) {
                try {
                    productVariantRepository.deleteByProductId(productId);
                } catch (Exception e) {
                    log.warn("[DummyProductInitializer] delete options failed. productId={}", productId, e);
                }
            }
        }
    }

    private void safeDeleteThumbnailsByProductIds(List<Long> productIds) {
        try {
            // ✅ 썸네일 repo는 보통 bulk가 있음 (네가 try-catch로 이미 쓰고 있음)
            productThumbnailRepository.deleteByProductIdIn(productIds);
            log.info("[DummyProductInitializer] deleted thumbnails (bulk): {}", productIds.size());
        } catch (Exception e) {
            log.warn("[DummyProductInitializer] delete thumbnails failed. size={}", productIds.size(), e);
        }
    }

    /**
     * ✅ 더미 상품 생성
     * - Product 엔티티 그래프(썸네일/옵션)를 먼저 구성
     * - save 1번으로 저장되게 유지 (cascade + orphanRemoval 전제)
     */
    private void createDummyProducts(Category dummyCategory, ProductColor dummyColor) {
        // 2026-09 갱신: 예전엔 맨유 공식몰(mufc-live.cdn.scayle.cloud) 이미지를 외부 URL로 직접 링크했는데,
        // 그 쪽 URL이 수시로 바뀌거나 만료되면서 썸네일이 깨지는 문제가 반복됐다.
        // (자세한 내용은 arrangeFile/concepts/TROUBLESHOOTING.md 참고)
        // 그래서 실제 상품(adidas x Man Utd EQT Collection / Stone Roses Collection, store.manutd.com 기준)의
        // 이름·가격 정보는 그대로 살리되, 이미지는 이 프로젝트 static 리소스에 직접 저장해서 외부 서버 상태와
        // 무관하게 항상 동일하게 뜨도록 함 (resources/static/images/dummy/).
        List<String> productNames = List.of(
                "Manchester United x adidas EQT Track Top Black",       // 슬라이드1 배너(adidas x Man Utd 트레이닝룩) 연동
                "Manchester United x adidas EQT Half Zip Top Black",
                "Manchester United x adidas EQT Jersey Red",
                "Manchester United x adidas EQT Sweatshirt Red",
                "Manchester United x adidas EQT Shorts Black",
                "Manchester United x adidas Stone Roses Jersey Blue",   // 슬라이드2 배너(adidas x Man Utd x Stone Roses) 연동
                "Manchester United x adidas Stone Roses Track Jacket Black",
                "Manchester United x adidas Stone Roses Bucket Hat Blue",
                "Manchester United x adidas Stone Roses Scarf Multi",
                "Manchester United x adidas Stone Roses T-Shirt White"
        );

        // store.manutd.com 실제 판매가 (원화, 2026-09 기준)
        List<BigDecimal> prices = List.of(
                BigDecimal.valueOf(159_500),
                BigDecimal.valueOf(159_500),
                BigDecimal.valueOf(131_300),
                BigDecimal.valueOf(150_100),
                BigDecimal.valueOf(84_500),
                BigDecimal.valueOf(168_900),
                BigDecimal.valueOf(187_600),
                BigDecimal.valueOf(71_300),
                BigDecimal.valueOf(71_300),
                BigDecimal.valueOf(75_100)
        );

        // classpath(static) 기준 상대경로 — 빌드 시 JAR에 함께 패키징되어 외부 서버 상태와 무관하게 항상 서빙됨
        List<String> imagePaths = List.of(
                "/images/dummy/EQT_tracktop.webp",
                "/images/dummy/EQT_halfzip.avif",
                "/images/dummy/EQT_jersey.avif",
                "/images/dummy/EQT_SweatshirtRed.avif",
                "/images/dummy/EQT_ShortsBlack.jpg",
                "/images/dummy/StoneRoses_jersey_short.jpg",
                "/images/dummy/StoneRoses_trackjacket.webp",
                "/images/dummy/StoneRoses_BucketHatBlue.avif",
                "/images/dummy/StoneRoses_ScarfMulti.avif",
                "/images/dummy/StoneRoses_T-ShirtWhite.avif"
        );

        int count = Math.min(productNames.size(), imagePaths.size());
        if (productNames.size() != imagePaths.size()) {
            log.warn("[Dummy] productNames({}) != imagePaths({}) -> using {}",
                    productNames.size(), imagePaths.size(), count);
        }

        for (int i = 0; i < count; i++) {
            String name = productNames.get(i);
            BigDecimal price = prices.get(i);
            String path = imagePaths.get(i);

            try {
                Product product = createProduct(name, price);

                // 썸네일 1개 추가
                addThumbnail(product, path);

                // 옵션(성별 x 사이즈) 생성
                addOptions(product, dummyCategory, dummyColor);

                Product saved = productRepository.save(product);
                log.info("[Dummy] created product: {} (id={})", saved.getProductName(), saved.getProductId());

            } catch (Exception e) {
                log.error("[Dummy] failed product: {}", name, e);
            }
        }
    }

    private Product createProduct(String productName, BigDecimal price) {
        return Product.createDummy(
                productName,
                ProductType.values()[random.nextInt(ProductType.values().length)],
                price,
                productName + " 상세 정보",
                "MANUTD Official",
                true,
                random.nextInt(50),
                true
        );
    }

    private void addThumbnail(Product product, String imagePath) {
        String normalized = normalizeUrl(imagePath);
        if (normalized == null) {
            log.warn("[Dummy] skip invalid thumbnail path. product={}", product.getProductName());
            return;
        }
        product.addThumbnailPath(normalized);
    }

    private void addOptions(Product product, Category category, ProductColor color) {
        for (Gender gender : Gender.values()) {
            for (Size size : Size.values()) {
                product.addOption(
                        color,
                        category,
                        gender,
                        size,
                        DEFAULT_STOCK
                );
            }
        }
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isBlank()) return null;
        // 외부 URL(http/https) 또는 이 프로젝트 static 리소스를 가리키는 절대경로("/"로 시작)만 허용
        boolean isExternal = trimmed.startsWith("http://") || trimmed.startsWith("https://");
        boolean isLocalStaticPath = trimmed.startsWith("/");
        if (!isExternal && !isLocalStaticPath) return null;
        return trimmed;
    }
}
