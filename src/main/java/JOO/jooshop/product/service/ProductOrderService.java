package JOO.jooshop.product.service;

import JOO.jooshop.global.queries.Condition;
import JOO.jooshop.global.queries.OrderBy;
import JOO.jooshop.global.queries.ProductQueryHelper;
import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.entity.QProduct;
import JOO.jooshop.product.model.ProductListResponseDto;
import JOO.jooshop.product.repository.ProductColorRepository;
import JOO.jooshop.product.repository.ProductRepository;
import JOO.jooshop.thumbnail.entity.ProductThumbnail;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
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

    public final ProductRepository productRepository;
    public final ProductColorRepository productColorRepository;
    public final ModelMapper modelMapper;
    private final JPAQueryFactory queryFactory;

    /**
     * 필터링 및 정렬
     * @param page
     * @param size
     * @param condition
     * @param order
     * @return
     */
    public Page<ProductListResponseDto> getFilteredAndSortedProducts(int page, int size, Condition condition, OrderBy order, Long category, String keyword) {
        // 필터링
        BooleanBuilder filterBuilder = ProductQueryHelper.createFilterBuilder(condition, category, keyword, QProduct.product);

        // 정렬
        OrderSpecifier<?> orderSpecifier = ProductQueryHelper.getOrderSpecifier(order, product);

        // 필터링 및 정렬 적용
        List<Product> results = getFilteredAndSortedResults(orderSpecifier, filterBuilder, page, size);

        // 전체 카운트 조회 쿼리
        long totalCount = queryFactory.selectFrom(product)
                .where(filterBuilder)
                .fetch().size();
        // querydsl 개발진 측에서 fetchCount 와 groupby 를 함께 사용할 때 생기는 문제로 인해 fetchcount 함수를 deprecated 시켰다고함.
//        .fetchCount();

        // ProductListResponseDto 로 변환
        List<ProductListResponseDto> productList = mapToProductListResponseDto(results);

        /*
        List<ProductListResponseDto> productList = results.stream()
                .map(ProductListResponseDto::new)
                .collect(Collectors.toList());
        */
        return new PageImpl<>(productList, PageRequest.of(page, size), totalCount);
    }

    // 필터링 및 정렬 수행하는 메서드
    private List<Product> getFilteredAndSortedResults(OrderSpecifier orderSpecifier, BooleanBuilder filterBuilder, int page, int size) {
        return queryFactory.selectFrom(product)
                .leftJoin(product.productThumbnails).fetchJoin()
                .where(filterBuilder)
                .orderBy(orderSpecifier)
                .offset(page * size)
                .limit(size)
                .fetch();
    }

    // Product 리스트 -> ProductListResponseDto 리스트로 변환 메서드
    private List<ProductListResponseDto> mapToProductListResponseDto(List<Product> results) {
        return results.stream()
                .map(product -> { // Product -> ProductListResponseDto 변환
                    ProductListResponseDto ProductListResponseDto = modelMapper.map(product, ProductListResponseDto.class);
                    // ProductThumbnail 의 ImagesPath 를 매핑
                    ProductListResponseDto.setProductThumbnails(
                            product.getProductThumbnails().stream()
                                    .map(ProductThumbnail::getImagesPath)
                                    .toList()
                    );
                    return ProductListResponseDto;
                })
                .toList();
    }
}
