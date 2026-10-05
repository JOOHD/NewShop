package JOO.jooshop.cart.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cart")
public class CartViewController {

    // 장바구니 화면
    @GetMapping
    public String cartPage() {
        return "carts/cart";
    }
}
