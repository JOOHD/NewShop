package JOO.jooshop.global.dummy;

import JOO.jooshop.categorys.entity.Category;
import JOO.jooshop.categorys.repository.CategoryRepository;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.ProductColor;
import JOO.jooshop.product.entity.enums.Gender;
import JOO.jooshop.product.entity.enums.ProductType;
import JOO.jooshop.product.repository.ProductColorRepository;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.productManagement.entity.ProductManagement;
import JOO.jooshop.productManagement.entity.enums.Size;
import JOO.jooshop.productManagement.repository.ProductManagementRepository;
import JOO.jooshop.thumbnail.repository.ProductThumbnailRepositoryV1;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

@Slf4j
@Profile("local") // 운영/배포에서는 사용 x
@Component
@RequiredArgsConstructor
public class DummyProductInitializer implements CommandLineRunner { // 스프링 부트 시작 시, 자동 실행

    private static final String DUMMY_CATEGORY_NAME = "DUMMY";
    private static final String DUMMY_COLOR_NAME = "DUMMY_COLOR";
    private static final long DEFAULT_STOCK = 20L;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository productColorRepository;

    private final ProductThumbnailRepositoryV1 productThumbnailRepository;
    private final ProductManagementRepository productManagementRepository;

    private final Random random = new Random();

    @Override
    @Transactional
    public void run(String... args) {

        log.info("[DummyProductInitializer] START");

        resetDummyData(); // 기존 더미 삭제

        Category dummyCategory = getOrCreateDefaultCategory(); // 기본 카테고리/컬러 확보
        ProductColor dummyColor = getOrCreateDefaultColor();

        createDummyProducts(dummyCategory, dummyColor); // 새 더미 생성

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

        // 1) 옵션(ProductManagement) 먼저 삭제
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
            productManagementRepository.deleteByProductIdIn(productIds);
            log.info("[DummyProductInitializer] deleted options (bulk): {}", productIds.size());
        } catch (Exception bulkFail) {
            // ✅ bulk 메서드가 없거나 실패하면 단수 delete로 fallback
            log.warn("[DummyProductInitializer] bulk delete options failed -> fallback to single delete. size={}",
                    productIds.size(), bulkFail);

            for (Long productId : productIds) {
                try {
                    productManagementRepository.deleteByProductId(productId);
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
        List<String> productNames = List.of(
                "2025 맨유 홈 저지",
                "2025 맨유 어웨이 저지",
                "2025 맨유 서드 저지",
                "2025 맨유 트레이닝 웨어",
                "2025 맨유 롱슬리브",
                "맨유 키즈 홈 저지",
                "맨유 액세서리 모자",
                "맨유 액세서리 스카프",
                "맨유 AWAY 긴팔 티",
                "맨유 THIRD 반팔 티"
        );

        List<String> ImagesUrls = List.of(
                "https://mufc-live.cdn.scayle.cloud/Images/aaf0f931ca9bbbca34f519531b1cc8ff.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/37253ea8264864e69d9c5dfdd28b8569.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/4644041696bbde141051021ef2325329.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/4f8dee4dd3a396ed6ed70d46c07982bd.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/3b1b1f35b7a3d63c879e9c8d98faedb7.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/2ef4f47f9fa4f0e5c01a9e60f9a25e2e.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/9a1b7b13bda76b6d64b3c35f8e1f90c4.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/8c1d09e79b3e55e4e80f4c7f2df08a2e.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/6f7a0b7b3e36f12f0b2cbf56c54dcd30.jpg?width=600",
                "https://mufc-live.cdn.scayle.cloud/Images/1f8c5f8c0e4a94c7b8b6e6a5bb2c6d9a.jpg?width=600"
        );

        int count = Math.min(productNames.size(), ImagesUrls.size());
        if (productNames.size() != ImagesUrls.size()) {
            log.warn("[Dummy] productNames({}) != ImagesUrls({}) -> using {}",
                    productNames.size(), ImagesUrls.size(), count);
        }

        for (int i = 0; i < count; i++) {
            String name = productNames.get(i);
            String url = ImagesUrls.get(i);

            try {
                Product product = createProduct(name);

                // 썸네일 1개 추가
                addThumbnail(product, url);

                // 옵션(성별 x 사이즈) 생성
                addOptions(product, dummyCategory, dummyColor);

                Product saved = productRepository.save(product);
                log.info("[Dummy] created product: {} (id={})", saved.getProductName(), saved.getProductId());

            } catch (Exception e) {
                log.error("[Dummy] failed product: {}", name, e);
            }
        }
    }

    private Product createProduct(String productName) {
        return Product.createDummy(
                productName,
                ProductType.values()[random.nextInt(ProductType.values().length)],
                BigDecimal.valueOf(100_000 + random.nextInt(200_000)),
                productName + " 상세 정보",
                "MANUTD Official",
                true,
                random.nextInt(50),
                true
        );
    }

    private void addThumbnail(Product product, String ImagesUrl) {
        String normalized = normalizeUrl(ImagesUrl);
        if (normalized == null) {
            log.warn("[Dummy] skip invalid thumbnail url. product={}", product.getProductName());
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
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) return null;
        return trimmed;
    }
}
