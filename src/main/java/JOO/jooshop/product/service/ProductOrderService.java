package JOO.jooshop.product.service;

import JOO.jooshop.global.queries.Condition;
import JOO.jooshop.global.queries.OrderBy;
import JOO.jooshop.global.queries.ProductQueryHelper;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.QProduct;
import JOO.jooshop.product.model.ProductListResponseDto;
import JOO.jooshop.product.repository.ProductRepository;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static JOO.jooshop.product.entity.QProduct.product;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
@Slf4j
public class ProductOrderService {

    private final ProductRepository productRepository;
    private final JPAQueryFactory queryFactory;

    /**
     * 조건(Condition), 정렬(OrderBy), 카테고리, 검색어를 조합한 상품 목록 페이징 조회.
     */
    public Page<ProductListResponseDto> getFilteredAndSortedProducts(
            int page, int size, Condition condition, OrderBy order, Long category, String keyword
    ) {
        BooleanBuilder filterBuilder = ProductQueryHelper.createFilterBuilder(condition, category, keyword, QProduct.product);
        OrderSpecifier<?> orderSpecifier = ProductQueryHelper.getOrderSpecifier(order, product);

        List<Product> results = fetchFiltered(orderSpecifier, filterBuilder, page, size);

        // fetchCount deprecated 이슈로 fetch().size() 사용
        long totalCount = queryFactory.selectFrom(product)
                .where(filterBuilder)
                .fetch().size();

        List<ProductListResponseDto> productList = results.stream()
                .map(ProductListResponseDto::new)
                .toList();

        return new PageImpl<>(productList, PageRequest.of(page, size), totalCount);
    }

    private List<Product> fetchFiltered(OrderSpecifier<?> orderSpecifier, BooleanBuilder filterBuilder, int page, int size) {
        return queryFactory.selectFrom(product)
                .leftJoin(product.productThumbnails).fetchJoin()
                .where(filterBuilder)
                .orderBy(orderSpecifier)
                .offset((long) page * size)
                .limit(size)
                .fetch();
    }
}
