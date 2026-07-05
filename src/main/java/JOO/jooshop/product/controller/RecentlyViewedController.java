package JOO.jooshop.product.controller;

import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import JOO.jooshop.product.model.ProductListResponseDto;
import JOO.jooshop.product.service.RecentlyViewedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 최근 본 상품 API
 *
 * Redis ZSET 기반 개인화 기능.
 * 본인 데이터만 조회 가능 — verifyUserIdMatch로 검증.
 */
@RestController
@RequestMapping("/api/v1/recently-viewed")
@RequiredArgsConstructor
public class RecentlyViewedController {

    private final RecentlyViewedService recentlyViewedService;

    /**
     * 최근 본 상품 목록 조회 (최신순, 최대 10개)
     *
     * GET /api/v1/recently-viewed/{memberId}
     *
     * 응답 예시:
     * [
     *   { "productId": 3, "productName": "나이키 에어맥스", "price": 159000, ... },
     *   { "productId": 1, "productName": "아디다스 삼바", "price": 129000, ... }
     * ]
     */
    @GetMapping("/{memberId}")
    public ResponseEntity<List<ProductListResponseDto>> getRecentlyViewed(
            @PathVariable Long memberId
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);  // 본인 데이터만 허용
        return ResponseEntity.ok(recentlyViewedService.getRecentlyViewed(memberId));
    }
}
