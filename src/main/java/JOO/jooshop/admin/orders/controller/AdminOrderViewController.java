package JOO.jooshop.admin.orders.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderViewController {

    // 관리자 주문 목록 화면
    @GetMapping("/list")
    public String orderListPage() {
        return "admin/orders/orderList";
    }
}
