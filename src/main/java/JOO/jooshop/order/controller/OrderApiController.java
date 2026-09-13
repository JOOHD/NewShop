package JOO.jooshop.order.controller;

import JOO.jooshop.global.authentication.support.AuthenticatedMemberResolver;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.model.OrderListResponse;
import JOO.jooshop.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주문 API 컨트롤러
 *
 * [리팩토링 — Before/After]
 *
 * Before:
 *   POST /api/v1/order/create  → createOrder()  (Redis 임시 저장)
 *   GET  /api/v1/order/temp/{memberId} → getTemporaryOrder() (Redis 조회)
 *   POST /api/v1/order/confirm → confirmOrder() (Redis → DB)
 *
 * After:
 *   POST /api/v1/order/confirm → confirmOrder() (Cart → DB 직접)
 *   → 불필요한 2단계 제거. 단순하고 명확한 흐름.
 */
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;

    /**
     * 주문 확정 — 장바구니 선택 → DB 직접 저장
     *
     * 요청 body: { memberId, cartIds, postCode, address, payMethod, ... }
     * 응답: 주문 번호
     *
     * 내부 흐름:
     *   1. cartIds로 Cart 조회 (DB)
     *   2. 본인 장바구니 검증
     *   3. Orders + OrderProduct 생성 → DB 저장
     *   4. 이후 PaymentController에서 결제 완료 처리
     */
    @PostMapping("/confirm")
    public ResponseEntity<String> confirmOrder(
            @Valid @RequestBody OrderDto orderDto
    ) {
        Orders order = orderService.confirmOrder(orderDto);
        return ResponseEntity.ok("주문이 완료되었습니다. 주문번호: " + order.getOrderId());
    }

    /**
     * 내 주문내역 목록 조회 — 로그인 방식(폼/소셜) 상관없이 memberId만 추출해서 조회
     */
    @GetMapping("/my")
    public ResponseEntity<List<OrderListResponse>> getMyOrders(Authentication authentication) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        return ResponseEntity.ok(orderService.getMyOrders(memberId));
    }
}
