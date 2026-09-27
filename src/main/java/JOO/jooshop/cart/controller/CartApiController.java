package JOO.jooshop.cart.controller;

import JOO.jooshop.cart.model.CartDto;
import JOO.jooshop.cart.model.CartRequestDto;
import JOO.jooshop.cart.model.CartResponse;
import JOO.jooshop.cart.model.CartUpdateDto;
import JOO.jooshop.cart.service.CartService;
import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.exception.ResponseMessageConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "장바구니", description = "장바구니 조회/추가/수정/삭제")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartApiController {

    /**
     * [Controller]

     * 기존
     * - 단순 CRUD 형태로 장바구니 처리
     * - memberId를 클라이언트에서 직접 전달받는 구조 존재
     * - 사용자 인증/식별 책임이 불명확
     *
     * refactoring 26.04
     * - 인증된 사용자 기준으로 장바구니 다루도록 정리
     * - 기존 DTO에서 memberId를 다시 받는 대신, 인증 사용자 ID를 추출해서 사용
     *    ㄴ즉, 클라이언트가 임의의 memberId를 보내서 다른 회원 장바구니 조작 x
     * - 회원 식별은 요청 body가 아닌, JWT 인증 정보로 처리하도록 보안 구조 일원화
     */

    private final CartService cartService;

    @PostMapping("/add/{inventoryId}")
    public ResponseEntity<String> addCart(@Valid @RequestBody CartRequestDto request,
                                          @PathVariable Long inventoryId,
                                          @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long memberId = userDetails.getMemberId();
        Long createdId = cartService.addCart(memberId, inventoryId, request.getQuantity());

        return ResponseEntity.ok("장바구니에 추가 되었습니다. cart_id : " + createdId);
    }

    @GetMapping("/my")
    public ResponseEntity<CartResponse> getMyCarts(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long memberId = userDetails.getMemberId();
        List<CartDto> carts = cartService.allCarts(memberId);

        return ResponseEntity.ok(new CartResponse(memberId, carts));
    }

    @PutMapping("/{cartId}")
    public ResponseEntity<CartDto> updateCart(@PathVariable Long cartId,
                                              @Valid @RequestBody CartUpdateDto request,
                                              @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long memberId = userDetails.getMemberId();
        CartDto updatedCartDto = cartService.updateCart(memberId, cartId, request);

        return ResponseEntity.ok(updatedCartDto);
    }

    @DeleteMapping("/{cartId}")
    public ResponseEntity<String> deleteCart(@PathVariable Long cartId,
                                             @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long memberId = userDetails.getMemberId();
        cartService.deleteCart(cartId, memberId);

        return ResponseEntity.ok(ResponseMessageConstants.DELETE_SUCCESS);
    }

    @DeleteMapping("/batch-delete")
    public ResponseEntity<String> deleteCartList(@RequestBody List<Long> cartIds,
                                                 @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long memberId = userDetails.getMemberId();
        cartService.deleteCartList(cartIds, memberId);

        return ResponseEntity.ok(ResponseMessageConstants.DELETE_SUCCESS);
    }
}