package JOO.jooshop.global.queries;

import JOO.jooshop.product.entity.QProduct;
import JOO.jooshop.product.entity.enums.ProductType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;

import java.time.LocalDateTime;

/**
 * Product QueryDSL 동적 조건 생성을 담당하는 Helper 클래스입니다.
 *
 * 역할:
 * - 상품 목록 조회 시 정렬 조건 생성
 * - 조건/카테고리/키워드 기반 where 조건 생성
 * - Repository QueryDSL 코드의 중복 제거
 */
public final class ProductQueryHelper {

    private ProductQueryHelper() {
    }

    /**
     * 상품 목록 정렬 조건을 생성합니다.
     */
    public static OrderSpecifier<?> getOrderSpecifier(OrderBy order, QProduct product) {
        if (order == null) {
            return product.createdAt.desc();
        }

        return switch (order) {
            case LATEST -> product.createdAt.desc();
            case POPULAR -> product.wishListCount.desc();
            case LOW_PRICE -> product.price.asc();
            case HIGH_PRICE -> product.price.desc();
            case HIGH_DISCOUNT_RATE -> product.discountRate.desc();
        };
    }

    /**
     * 상품 목록 필터 조건을 생성합니다.
     */
    public static BooleanBuilder createFilterBuilder(
            Condition condition,
            Long categoryId,
            String keyword,
            QProduct product
    ) {
        BooleanBuilder builder = new BooleanBuilder();

        addConditionFilter(builder, condition, product);
        addCategoryFilter(builder, categoryId, product);
        addKeywordFilter(builder, keyword, product);

        return builder;
    }

    /**
     * 조건 필터를 추가합니다.
     */
    private static void addConditionFilter(
            BooleanBuilder builder,
            Condition condition,
            QProduct product
    ) {
        if (condition == null) {
            return;
        }

        switch (condition) {
            case NEW ->
                    builder.and(product.createdAt.after(LocalDateTime.now().minusMonths(1)));

            case BEST ->
                    builder.and(product.wishListCount.goe(30L));

            case DISCOUNT ->
                    builder.and(product.isDiscount.isTrue());

            case RECOMMEND ->
                    builder.and(product.isRecommend.isTrue());

            case MAN ->
                    builder.and(product.productType.eq(ProductType.MAN));

            case WOMAN ->
                    builder.and(product.productType.eq(ProductType.WOMAN));

            case UNISEX ->
                    builder.and(product.productType.eq(ProductType.UNISEX));
        }
    }

    /**
     * 카테고리 필터를 추가합니다.
     *
     * categoryId와 직접 연결된 상품 또는
     * 해당 categoryId를 부모 카테고리로 가진 상품을 조회합니다.
     */
    private static void addCategoryFilter(
            BooleanBuilder builder,
            Long categoryId,
            QProduct product
    ) {
        if (categoryId == null) {
            return;
        }

        builder.andAnyOf(
                product.productManagements.any().category.categoryId.eq(categoryId),
                product.productManagements.any().category.parent.categoryId.eq(categoryId)
        );
    }

    /**
     * 키워드 검색 조건을 추가합니다.
     */
    private static void addKeywordFilter(
            BooleanBuilder builder,
            String keyword,
            QProduct product
    ) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        String trimmedKeyword = keyword.trim();

        builder.and(
                product.productName.containsIgnoreCase(trimmedKeyword)
                        .or(product.productInfo.containsIgnoreCase(trimmedKeyword))
        );
    }
}