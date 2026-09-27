package JOO.jooshop.admin.orders.controller;

import JOO.jooshop.admin.orders.model.AdminOrderDetailResponse;
import JOO.jooshop.admin.orders.model.AdminOrderListResponse;
import JOO.jooshop.admin.orders.service.AdminOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "관리자 - 주문", description = "관리자 전용 주문 조회/상태 변경")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderApiController {

    private final AdminOrderService adminOrderService;

    /**
     * 관리자 주문 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<AdminOrderListResponse>> getOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(adminOrderService.getOrders(status, keyword));
    }

    /**
     * 관리자 주문 상세 조회
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        return ResponseEntity.ok(adminOrderService.getOrderDetail(orderId));
    }
}