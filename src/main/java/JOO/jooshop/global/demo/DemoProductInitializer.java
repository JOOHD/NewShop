package JOO.jooshop.global.demo;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.repository.CategoryRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.product.repository.ProductColorRepository;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.product.service.ProductRankingService;
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
@Order(1) // DemoProductViewsInitializer(조회수 시딩)보다 먼저 실행되어야 함 — 상품이 먼저 있어야 조회수를 심을 수 있음
public class DemoProductInitializer implements CommandLineRunner { // 스프링 부트 시작 시, 자동 실행

    private static final String DEMO_COLOR_NAME = "DEMO_COLOR";
    private static final long DEFAULT_STOCK = 20L;

    // [2026-10-05] 카테고리를 2단계 트리로 변경: 유니폼 / 패션(상의, 하의) / 악세사리
    // 예전엔 Origin / Collab / Acc 3개 평면 구조였고, "콜라보"는 카테고리가 아니라
    // 상품 이름의 " x "(협업 표기)로 구분한다 (/products?collab=true).
    private static final String CATEGORY_UNIFORM = "유니폼";
    private static final String CATEGORY_FASHION = "패션";
    private static final String CATEGORY_TOPS = "상의";
    private static final String CATEGORY_BOTTOMS = "하의";
    private static final String CATEGORY_ACCESSORY = "악세사리";

    // 이전 구조의 카테고리 이름 — 기존 DB(운영)의 옵션을 새 트리로 옮길 때만 사용
    private static final String LEGACY_ORIGIN = "Origin";
    private static final String LEGACY_COLLAB = "Collab";
    private static final String LEGACY_ACC = "Acc";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository productColorRepository;

    private final ProductThumbnailRepositoryV1 productThumbnailRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductRankingService productRankingService;

    private final Random random = new Random();

    /**
     * [정책 변경 — 운영/로컬 통일]
     * 예전엔 재기동마다 데모 상품을 삭제 후 재생성했지만(resetDemoData()),
     * 이 방식을 운영(EC2)에 그대로 적용하면 데모 상품을 참조하는 주문(OrderProduct FK)이
     * 있을 경우 삭제가 막히거나 부팅이 실패할 위험이 있었다.
     * → "데모 상품이 이미 있으면 손대지 않고, 없을 때만 최초 1회 생성"으로 변경.
     * 로컬/운영 모두 같은 로직을 쓰되, 한 번 생성된 데모 상품은 영구적으로 유지된다.
     * (완전히 새로 시딩하고 싶으면 DB에서 데모 상품을 직접 삭제하고 재기동 — resetDemoData()는
     *  지금은 run()에서 자동 호출되지 않지만, 필요하면 그대로 재사용 가능하도록 남겨둠)
     */
    @Override
    @Transactional
    public void run(String... args) {

        log.info("[DemoProductInitializer] START");

        // 카테고리 트리는 데모 상품이 이미 있어도 항상 보장하고, 옛 카테고리(Origin/Collab/Acc)는 새 트리로 이전한다.
        // 둘 다 여러 번 실행해도 결과가 같다(멱등) — 운영(ddl-auto: update) 재배포 때도 안전.
        ensureCategoryTree();
        migrateLegacyCategories();

        if (!productRepository.findDemoIds().isEmpty()) {
            log.info("[DemoProductInitializer] demo products already exist — skip (no reset)");
            return;
        }

        // [2026-09-28] Redis(조회수 ZSet)는 DB와 별개 저장소라 create-drop으로도 안 지워진다.
        // 데모 상품을 "진짜로 새로 만드는" 이 시점에만 함께 리셋해야, 로컬(create-drop, 매번 재생성)은
        // 재기동마다 죽은 옛 상품 ID의 조회수 흔적이 안 쌓이고, 운영(update, 최초 1회만 생성)은
        // 이 블록 자체가 서비스 최초 부팅 때 딱 한 번만 실행되니 실 트래픽 조회수를 건드릴 일이 없다.
        productRankingService.resetViews();

        ProductColor demoColor = getOrCreateDefaultColor(); // 색상은 카테고리와 무관하게 공용 하나만 사용

        createDemoProducts(demoColor); // 최초 1회만 생성

        log.info("[DemoProductInitializer] END");
    }

    // 카테고리 트리(유니폼 / 패션 > 상의, 하의 / 악세사리)가 없으면 생성
    private void ensureCategoryTree() {
        getOrCreateRootCategory(CATEGORY_UNIFORM);
        Category fashion = getOrCreateRootCategory(CATEGORY_FASHION);
        getOrCreateRootCategory(CATEGORY_ACCESSORY);

        getOrCreateChildCategory(fashion, CATEGORY_TOPS);
        getOrCreateChildCategory(fashion, CATEGORY_BOTTOMS);
    }

    // 최상위 카테고리 조회, 없으면 생성
    private Category getOrCreateRootCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(new Category(0L, name)));
    }

    // 하위 카테고리 조회, 없으면 부모 아래에 생성
    private Category getOrCreateChildCategory(Category parent, String name) {
        return categoryRepository.findByName(name).orElseGet(() -> {
            Category child = categoryRepository.save(new Category(parent, parent.getDepth() + 1, name));
            parent.getChildren().add(child);
            return child;
        });
    }

    // 옛 카테고리(Origin/Collab/Acc)에 걸린 옵션을 새 카테고리로 옮기고 옛 카테고리를 삭제 (없으면 아무것도 안 함)
    private void migrateLegacyCategories() {
        for (String legacyName : List.of(LEGACY_ORIGIN, LEGACY_COLLAB, LEGACY_ACC)) {
            Category legacy = categoryRepository.findByName(legacyName).orElse(null);
            if (legacy == null) {
                continue;
            }

            List<ProductVariant> variants = productVariantRepository.findByCategory(legacy);
            for (ProductVariant variant : variants) {
                variant.changeCategory(resolveNewCategory(legacyName, variant.getProduct().getProductName()));
            }
            productVariantRepository.flush();
            categoryRepository.delete(legacy);
            log.info("[Demo] migrated legacy category '{}' ({} variants)", legacyName, variants.size());
        }
    }

    // 옛 카테고리 + 상품명으로 새 카테고리 결정 (Origin→유니폼, Collab→상의, Acc→반바지는 하의/나머지는 악세사리)
    private Category resolveNewCategory(String legacyName, String productName) {
        String newName;
        if (LEGACY_ORIGIN.equals(legacyName)) {
            newName = CATEGORY_UNIFORM;
        } else if (LEGACY_COLLAB.equals(legacyName)) {
            newName = CATEGORY_TOPS;
        } else {
            newName = productName.contains("Shorts") ? CATEGORY_BOTTOMS : CATEGORY_ACCESSORY;
        }
        return categoryRepository.findByName(newName).orElseThrow();
    }

    // 이름으로 카테고리 조회, 없으면 생성
    private Category getOrCreateCategory(String name) {
        return categoryRepository.findByName(name)
                .orElseGet(() -> categoryRepository.save(Category.ofName(name)));
    }

    // 데모용 기본 색상 조회, 없으면 생성
    private ProductColor getOrCreateDefaultColor() {
        return productColorRepository.findByColor(DEMO_COLOR_NAME)
                .orElseGet(() -> productColorRepository.save(ProductColor.ofName(DEMO_COLOR_NAME)));
    }

    /**
     * reset = 기존 데모 데이터만 삭제
     *
     * 전제:
     * - productRepository.findDemoIds() : 데모로 판단되는 product id 리스트 반환
     * - 썸네일/옵션은 FK 때문에 먼저 삭제 후 product 삭제
     *
     * 삭제 전략:
     * 1) bulk delete 메서드 있으면 bulk로
     * 2) 없으면 (레포가 단수만 있으면) 반복 삭제로 fallback
     */
    protected void resetDemoData() {
        log.info("[DemoProductInitializer] delete demo data only");

        List<Long> demoIds = productRepository.findDemoIds();
        if (demoIds == null || demoIds.isEmpty()) {
            log.info("[DemoProductInitializer] no demo data to delete");
            return;
        }

        // 1) 옵션(ProductVariant) 먼저 삭제
        safeDeleteOptionsByProductIds(demoIds);

        // 2) 썸네일 먼저 삭제
        safeDeleteThumbnailsByProductIds(demoIds);

        // 3) Product 삭제 (batch)
        productRepository.deleteAllByIdInBatch(demoIds);

        log.info("[DemoProductInitializer] deleted demo products: {}", demoIds.size());
    }

    // 데모 상품 옵션 삭제 (일괄 삭제 실패 시 개별 삭제)
    private void safeDeleteOptionsByProductIds(List<Long> productIds) {
        try {
            // ✅ bulk 메서드가 있으면 이걸 쓰는 게 최적
            productVariantRepository.deleteByProductIdIn(productIds);
            log.info("[DemoProductInitializer] deleted options (bulk): {}", productIds.size());
        } catch (Exception bulkFail) {
            // ✅ bulk 메서드가 없거나 실패하면 단수 delete로 fallback
            log.warn("[DemoProductInitializer] bulk delete options failed -> fallback to single delete. size={}",
                    productIds.size(), bulkFail);

            for (Long productId : productIds) {
                try {
                    productVariantRepository.deleteByProductId(productId);
                } catch (Exception e) {
                    log.warn("[DemoProductInitializer] delete options failed. productId={}", productId, e);
                }
            }
        }
    }

    // 데모 상품 썸네일 삭제
    private void safeDeleteThumbnailsByProductIds(List<Long> productIds) {
        try {
            // ✅ 썸네일 repo는 보통 bulk가 있음 (네가 try-catch로 이미 쓰고 있음)
            productThumbnailRepository.deleteByProductIdIn(productIds);
            log.info("[DemoProductInitializer] deleted thumbnails (bulk): {}", productIds.size());
        } catch (Exception e) {
            log.warn("[DemoProductInitializer] delete thumbnails failed. size={}", productIds.size(), e);
        }
    }

    /**
     * ✅ 데모 상품 생성
     * - Product 엔티티 그래프(썸네일/옵션)를 먼저 구성
     * - save 1번으로 저장되게 유지 (cascade + orphanRemoval 전제)
     */
    private void createDemoProducts(ProductColor demoColor) {
        // 2026-09 갱신: 예전엔 맨유 공식몰(mufc-live.cdn.scayle.cloud) 이미지를 외부 URL로 직접 링크했는데,
        // 그 쪽 URL이 수시로 바뀌거나 만료되면서 썸네일이 깨지는 문제가 반복됐다.
        // (자세한 내용은 arrangeFile/concepts/TROUBLESHOOTING.md 참고)
        // 그래서 실제 상품(adidas x Man Utd EQT Collection / Stone Roses Collection, store.manutd.com 기준)의
        // 이름·가격 정보는 그대로 살리되, 이미지는 이 프로젝트 static 리소스에 직접 저장해서 외부 서버 상태와
        // 무관하게 항상 동일하게 뜨도록 함 (resources/static/images/demo/).
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
                "/images/demo/EQT_tracktop.webp",
                "/images/demo/EQT_halfzip.avif",
                "/images/demo/EQT_jersey.avif",
                "/images/demo/EQT_SweatshirtRed.avif",
                "/images/demo/EQT_ShortsBlack.jpg",
                "/images/demo/StoneRoses_jersey_short.jpg",
                "/images/demo/StoneRoses_trackjacket.webp",
                "/images/demo/StoneRoses_BucketHatBlue.avif",
                "/images/demo/StoneRoses_ScarfMulti.avif",
                "/images/demo/StoneRoses_T-ShirtWhite.avif"
        );

        // 상품 성격에 맞춘 카테고리 매핑 — 유니폼(저지) 2 / 상의 5 / 하의 1 / 악세사리 2
        List<String> categoryNames = List.of(
                CATEGORY_TOPS,          // EQT Track Top Black
                CATEGORY_TOPS,          // EQT Half Zip Top Black
                CATEGORY_UNIFORM,       // EQT Jersey Red
                CATEGORY_TOPS,          // EQT Sweatshirt Red
                CATEGORY_BOTTOMS,       // EQT Shorts Black
                CATEGORY_UNIFORM,       // Stone Roses Jersey Blue
                CATEGORY_TOPS,          // Stone Roses Track Jacket Black
                CATEGORY_ACCESSORY,     // Stone Roses Bucket Hat Blue
                CATEGORY_ACCESSORY,     // Stone Roses Scarf Multi
                CATEGORY_TOPS           // Stone Roses T-Shirt White
        );

        int count = Math.min(productNames.size(), imagePaths.size());
        if (productNames.size() != imagePaths.size()) {
            log.warn("[Demo] productNames({}) != imagePaths({}) -> using {}",
                    productNames.size(), imagePaths.size(), count);
        }

        for (int i = 0; i < count; i++) {
            String name = productNames.get(i);
            BigDecimal price = prices.get(i);
            String path = imagePaths.get(i);
            Category category = getOrCreateCategory(categoryNames.get(i));

            try {
                Product product = createProduct(name, price);

                // 썸네일 1개 추가
                addThumbnail(product, path);

                // 옵션(성별 x 사이즈) 생성
                addOptions(product, category, demoColor);

                Product saved = productRepository.save(product);
                log.info("[Demo] created product: {} (id={}, category={})", saved.getProductName(), saved.getProductId(), category.getName());

            } catch (Exception e) {
                log.error("[Demo] failed product: {}", name, e);
            }
        }
    }

    // 데모 상품 생성 (상품 유형은 랜덤)
    private Product createProduct(String productName, BigDecimal price) {
        return Product.createDemo(
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

    // 유효한 경로일 때만 상품에 썸네일 추가
    private void addThumbnail(Product product, String imagePath) {
        String normalized = normalizeUrl(imagePath);
        if (normalized == null) {
            log.warn("[Demo] skip invalid thumbnail path. product={}", product.getProductName());
            return;
        }
        product.addThumbnailPath(normalized);
    }

    // 성별 × 사이즈 조합으로 상품 옵션 생성
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

    // 이미지 경로 검증 — 외부 URL 또는 "/"로 시작하는 정적 경로만 허용
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
