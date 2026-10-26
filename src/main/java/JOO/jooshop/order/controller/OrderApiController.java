package JOO.jooshop.order.controller;

import JOO.jooshop.global.authentication.support.AuthenticatedMemberResolver;
import JOO.jooshop.order.entity.Orders;
import JOO.jooshop.order.model.OrderConfirmResponse;
import JOO.jooshop.order.model.OrderDto;
import JOO.jooshop.order.model.OrderListResponse;
import JOO.jooshop.order.model.OrderPreviewRequest;
import JOO.jooshop.order.model.OrderPreviewResponse;
import JOO.jooshop.order.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "주문", description = "주문 생성/조회")
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderService orderService;

    /**
     * 주문 확정 — 장바구니 선택 → DB 직접 저장
     *
     * 요청 body: { memberId, cartIds, postCode, address, payMethod, ... }
     * 응답: { orderId, merchantUid, amount, orderName } — 서버가 확정한 주문번호와 결제 금액
     *
     * 내부 흐름:
     *   1. cartIds로 Cart 조회 (DB)
     *   2. 본인 장바구니 검증
     *   3. 재고 차감 + Orders(결제 대기, PENDING) + OrderProduct 생성 → DB 저장
     *   4. 화면이 응답의 merchantUid/amount로 결제창을 띄우고, 결제 후 PaymentController가 검증해 완료 처리
     *   5. 결제하지 않으면 만료 배치가 주문을 취소하고 재고를 되돌린다
     */
    @PostMapping("/confirm")
    public ResponseEntity<OrderConfirmResponse> confirmOrder(
            @Valid @RequestBody OrderDto orderDto
    ) {
        Orders order = orderService.confirmOrder(orderDto);
        return ResponseEntity.ok(OrderConfirmResponse.from(order));
    }

    /**
     * 금액 미리보기 — 주문을 만들지 않고 상품 합계·배송비·결제 금액을 서버 규칙으로 계산해 돌려준다.
     * 요청: { cartIds: [...] } (장바구니) 또는 { items: [{variantId, quantity}] } (바로 구매)
     * 화면은 금액·배송비를 직접 계산하지 않고 이 응답을 표시한다. 회원은 요청 본문이 아니라 로그인 정보에서 꺼낸다.
     */
    @PostMapping("/preview")
    public ResponseEntity<OrderPreviewResponse> previewOrder(
            @Valid @RequestBody OrderPreviewRequest request,
            Authentication authentication
    ) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        return ResponseEntity.ok(orderService.previewOrder(memberId, request));
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
