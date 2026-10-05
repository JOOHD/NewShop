package JOO.jooshop.admin.orders.service;

import JOO.jooshop.admin.orders.model.AdminOrderDetailResponse;
import JOO.jooshop.admin.orders.model.AdminOrderListResponse;
import JOO.jooshop.admin.orders.repository.AdminOrderRepository;
import JOO.jooshop.order.entity.Orders;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderService {

    private final AdminOrderRepository adminOrderRepository;

    /**
     * 관리자용 주문 목록 조회
     * - status, keyword는 선택 필터
     */
    public List<AdminOrderListResponse> getOrders(String status, String keyword) {
        List<Orders> orders = adminOrderRepository.findAll(
                Sort.by(Sort.Direction.DESC, "orderDay")
        );

        return orders.stream()
                .filter(order -> isMatchedStatus(order, status))
                .filter(order -> isMatchedKeyword(order, keyword))
                .map(AdminOrderListResponse::from)
                .toList();
    }

    /**
     * 관리자용 주문 상세 조회
     */
    public AdminOrderDetailResponse getOrderDetail(Long orderId) {
        Orders order = adminOrderRepository.findWithOrderProductsByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 정보를 찾을 수 없습니다. orderId=" + orderId));

        return AdminOrderDetailResponse.from(order);
    }

    // 주문 상태 필터 조건 일치 여부 (값이 없으면 전체 통과)
    private boolean isMatchedStatus(Orders order, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return order.getPaymentStatus().name().equalsIgnoreCase(status);
    }

    // 검색어 필터 조건 일치 여부 — 주문자명 또는 상품명 포함 (값이 없으면 전체 통과)
    private boolean isMatchedKeyword(Orders order, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        return containsIgnoreCase(order.getOrdererName(), keyword)
                || containsIgnoreCase(order.getProductNameSummary(), keyword);
    }

    // 대소문자 구분 없이 문자열 포함 여부
    private boolean containsIgnoreCase(String source, String keyword) {
        if (source == null || keyword == null) {
            return false;
        }
        return source.toLowerCase().contains(keyword.toLowerCase());
    }
}