package JOO.jooshop.wishList.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WishListViewController {

    // 위시리스트 화면 — 실제 목록은 화면 로드 후 JS가 /api/v1/wishlist/my 를 fetch해서 렌더링
    @GetMapping("/wishlist")
    public String wishListPage() {
        return "wishlist/wishList";
    }
}
