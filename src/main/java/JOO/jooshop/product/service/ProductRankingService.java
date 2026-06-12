package JOO.jooshop.product.service;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.model.ProductRankResponseDto;
import JOO.jooshop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 상품 조회수 기반 랭킹 서비스.
 * Redis ZSet(정렬된 집합)을 활용해 실시간 조회수를 집계하고 랭킹 목록을 반환한다.
 */
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
@Slf4j
public class ProductRankingService {

    private static final String PRODUCT_VIEWS_KEY = "product_views";

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 조회수 기준 상위 N개 상품 목록 반환.
     * Redis ZSet에서 ID를 가져와 DB에서 Product를 조회한 뒤 DTO로 변환.
     */
    public List<ProductRankResponseDto> getProductListByRanking(int limit) {
        Set<String> productIds = getTopProductIds(limit);

        List<Long> productIdList = productIds.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        return productIdList.stream()
                .map(id -> productRepository.findByProductId(id).orElse(null))
                .filter(Objects::nonNull)
                .map(ProductRankResponseDto::new)
                .toList();
    }

    /** Redis ZSet에서 조회수 높은 순으로 상품 ID 조회 */
    public Set<String> getTopProductIds(int limit) {
        return redisTemplate.opsForZSet().reverseRange(PRODUCT_VIEWS_KEY, 0, limit - 1)
                .stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
    }

    /** 상품 조회 시 호출 — 조회수 1 증가 */
    public void increaseProductViews(Long productId) {
        redisTemplate.opsForZSet().incrementScore(PRODUCT_VIEWS_KEY, String.valueOf(productId), 1);
    }
}
