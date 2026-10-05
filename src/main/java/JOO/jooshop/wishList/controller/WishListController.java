package JOO.jooshop.wishList.controller;

import JOO.jooshop.global.authentication.support.AuthenticatedMemberResolver;
import JOO.jooshop.wishList.model.WishListResponseDto;
import JOO.jooshop.wishList.service.WishListService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "위시리스트", description = "좋아요한 상품 조회/추가/삭제")
@RestController
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishListController {

    private final WishListService wishListService;

    // 내 좋아요(위시리스트) 목록 조회
    @GetMapping("/my")
    public ResponseEntity<List<WishListResponseDto>> getMyWishList(Authentication authentication) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        return ResponseEntity.ok(wishListService.getMyWishList(memberId));
    }

    // 상품 좋아요 추가
    @PostMapping("/{productId}")
    public ResponseEntity<Void> add(@PathVariable Long productId, Authentication authentication) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        wishListService.add(memberId, productId);
        return ResponseEntity.ok().build();
    }

    // 상품 좋아요 삭제
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@PathVariable Long productId, Authentication authentication) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        wishListService.remove(memberId, productId);
        return ResponseEntity.ok().build();
    }
}
