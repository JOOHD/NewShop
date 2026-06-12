package JOO.jooshop.order.controller;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.model.TempOrderResponse;
import JOO.jooshop.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 주문 API 컨트롤러 — 요청 전달 역할만 수행.
 * 인가 검증은 MemberAuthorizationUtil 또는 OrderService 내부 verifyUserIdMatch로 통일.
 */
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;

    @GetMapping("/temp/{memberId}")
    public ResponseEntity<TempOrderResponse> getTemporaryOrder(
            @PathVariable Long memberId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return ResponseEntity.ok(orderService.getTemporaryOrder(memberId));
    }

    @PostMapping("/create")
    public ResponseEntity<String> createOrder(
            @Valid @RequestBody OrderDto orderDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        orderService.createOrder(orderDto.getCartIds(), orderDto);
        return ResponseEntity.ok("임시 주문이 Redis에 저장되었습니다.");
    }

    @PostMapping("/confirm")
    public ResponseEntity<String> confirmOrder(
            @RequestBody OrderDto orderDto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Orders order = orderService.confirmOrder(orderDto);
        return ResponseEntity.ok("주문이 확정되어 DB에 저장되었습니다. 주문번호: " + order.getOrderId());
    }
}
