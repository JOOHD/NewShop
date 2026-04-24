package JOO.jooshop.product.service;

import JOO.jooshop.product.entity.Product;
import JOO.jooshop.product.model.ProductRankResponseDto;
import JOO.jooshop.product.repository.ProductColorRepository;
import JOO.jooshop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
@Slf4j
public class ProductRankingService {

    /*
        1. 상품 조회수 증가 -> 사용자가 상품을 볼 떄마다 조회수를 올림.
        2. 상품 랭킹 조회 -> 조회수가 많은 상품을 상위순으로 가져옴.
        3. 상품 리스트 조회(랭킹순으로) -> 랭킹에 따라 상품 목록을 가져오고, DTO 로 변환해 반환.

        Redis : 실시간 랭킹 같은 빠른 데이터 처리를 위해 사용. ZSet(정렬된 집합) 활용
        ModelMapper : Entity -> DTO 변환을 편하게 하기 위해 사용

        - RedisTemplate<String, Object> + GenericJackson2JsonRedisSerializer 조합은 문자열 + 객체 모두 저장 가능
        - 단순 랭킹뿐 아니라, 제품 상세 캐시, 인기 상품 리스트, 유저 임시 정보 저장 등 다양한 기능에 대응
     */

    public final ProductRepository productRepository;
    public final ProductColorRepository productColorRepository;
    public final ModelMapper modelMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // 랭킹순으로 상품 리스트를 조회하는 메서드
    public List<ProductRankResponseDto> getProductListByRanking(int limit) {

        // Redis 에서 랭킹 순으로 상품 ID를 가져옴
        Set<String> productIds = getTopProductIds(limit);
        // Redis에서 가져온 ID는 문자열이라서 **숫자(Long)**으로 변환.
        List<Long> productIdList = productIds.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());

        // 상품 ID로 DB 에서 상품 조회, 해당 상품이 없으면 null 제외하고 필터링
        List<Product> products = productIdList.stream()
                .map(productId -> productRepository.findByProductId(productId).orElse(null))
                .filter(Objects::nonNull) // 상품이 없을 수도 있음 (null), 위 repository 방지
                .collect(Collectors.toList());

        // Product -> ProductRankResponseDto 변환
        return products.stream()
                .map(product -> {
                    ProductRankResponseDto dto = modelMapper.map(product, ProductRankResponseDto.class);
                    // 썸네일 리스트가 비어있을 경우, get(0) 을 아예 호출하지 않도록 설정
                    //  "Index 0 out of bounds for length 0" 에러 방지
                    if (!product.getProductThumbnails().isEmpty()) {
                        dto.setProductThumbnails(product.getProductThumbnails().get(0).getImagesPath());
                    }
                    return dto;
                })
                .toList();
    }

    // 랭킹을 위한 상품 조회수 가져오는 메서드
    public Set<String> getTopProductIds(int limit) {
        String key = "product_views";
        // Redis 에 있는 key 값을 ZSet 에서 '조회수가 높은 순서'로 상품 ID를 가져온다.
        return redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1)
                .stream()
                .map(String::valueOf) // Object -> String
                .collect(Collectors.toSet());
    }


    // 상품 조회수 증가 메서드 (ProductServiceV1 메서드)
    public void increaseProductViews(Long productId) {
        String key = "product_views"; // Redis 매핑
        // ProductServiceV1 class 의 productDetail 메서드에 호출
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), 1);
    }
}














