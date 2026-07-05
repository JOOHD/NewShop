package JOO.jooshop.product.service;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.product.model.ProductListResponseDto;
import JOO.jooshop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 최근 본 상품 서비스 (Redis ZSET 기반)
 *
 * 자료구조: Redis ZSet (Sorted Set)
 *   - key   : "recentView:{memberId}"
 *   - value : productId (String)
 *   - score : System.currentTimeMillis() → 최신순 정렬
 *
 * 특징:
 *   - 최대 10개 보관. 초과 시 오래된 항목 자동 제거
 *   - TTL 7일 — 만료 시 자동 삭제 (DB 쓰레기 없음)
 *   - 로그인 사용자만 기록. 비로그인은 skip
 *   - ProductRankingService의 "전체 조회수 집계"와 역할 분리:
 *       ProductRankingService → 모든 사용자 통합 조회수 (상품 인기도)
 *       RecentlyViewedService  → 개인별 최근 본 상품 (개인화)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecentlyViewedService {

    private static final String KEY_PREFIX = "recentView:";
    private static final int MAX_SIZE = 10;
    private static final long TTL_DAYS = 7;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ProductRepository productRepository;

    /**
     * 최근 본 상품 기록 (로그인 상태일 때만)
     *
     * SecurityContextHolder에서 memberId를 직접 꺼내 처리.
     * → productDetail() 호출부 변경 없이 자연스럽게 연동
     * → 비로그인 접근 시 조용히 skip (예외 없음)
     */
    public void recordIfAuthenticated(Long productId) {
        Long memberId = extractMemberId();
        if (memberId == null) return;

        String key = KEY_PREFIX + memberId;
        double score = System.currentTimeMillis();

        // ZSet에 상품 추가. 이미 있으면 score(시간) 업데이트 → 최신 조회 시점으로 갱신
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), score);

        // MAX_SIZE 초과 시 가장 오래된 것(score 낮은 것) 제거
        // removeRange(key, 0, -MAX_SIZE - 1) → 인덱스 0부터 (전체크기 - MAX_SIZE - 1)까지 삭제
        Long size = redisTemplate.opsForZSet().size(key);
        if (size != null && size > MAX_SIZE) {
            redisTemplate.opsForZSet().removeRange(key, 0, size - MAX_SIZE - 1);
        }

        // TTL 갱신 — 마지막 조회로부터 7일
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);

        log.debug("최근 본 상품 기록 — memberId={}, productId={}", memberId, productId);
    }

    /**
     * 최근 본 상품 목록 조회 (최신순)
     *
     * ZSet.reverseRange → score 높은 순(최근 순)으로 반환
     * productId 목록 → DB 조회 → DTO 변환
     */
    @Transactional(readOnly = true)
    public List<ProductListResponseDto> getRecentlyViewed(Long memberId) {
        String key = KEY_PREFIX + memberId;

        // score 내림차순(최신순)으로 productId 목록 조회
        Set<Object> ids = redisTemplate.opsForZSet().reverseRange(key, 0, MAX_SIZE - 1);
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        return ids.stream()
                .map(id -> productRepository.findByProductId(Long.parseLong(String.valueOf(id))).orElse(null))
                .filter(Objects::nonNull)
                .map(ProductListResponseDto::new)
                .toList();
    }

    /**
     * SecurityContextHolder에서 현재 로그인한 memberId 추출.
     * 비로그인(anonymousUser) 또는 인증 없으면 null 반환.
     */
    private Long extractMemberId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (!(auth.getPrincipal() instanceof CustomUserDetails userDetails)) return null;
        return userDetails.getMemberId();
    }
}
