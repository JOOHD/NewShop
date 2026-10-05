package JOO.jooshop.order.controller;

import JOO.jooshop.global.authentication.support.AuthenticatedMemberResolver;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class OrderViewController {

    private final MemberAccountService memberAccountService;

    // 폼 로그인(CustomUserDetails)/소셜 로그인(CustomOAuth2User) 둘 다 지원하기 위해
    // 임시 주문서 화면 — 로그인 방식(폼/소셜)에 상관없이 memberId를 꺼내 뷰에 전달
    // (@AuthenticationPrincipal CustomUserDetails로 직접 캐스팅하면 소셜 로그인에서 null이 됨)
    @GetMapping("/tempOrder")
    public String tempOrderPage(Authentication authentication, Model model) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        model.addAttribute("memberId", memberId);
        return "orders/tempOrder";
    }

    // 주문서 화면 — 로그인 회원의 이름/연락처를 미리 채워 전달
    @GetMapping("/order")
    public String orderPage(Authentication authentication, Model model) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        Member member = memberAccountService.findMemberById(memberId);

        model.addAttribute("username", member.getUsername());
        model.addAttribute("phoneNumber", member.getPhoneNumber());

        return "orders/order";
    }

    // 헤더의 "주문내역" 아이콘이 연결된 목록 페이지.
    // 실제 데이터는 화면 로드 후 JS가 /api/v1/order/my를 fetch해서 렌더링 (admin/orders/orderList.html과 동일한 패턴)
    @GetMapping("/orders")
    public String orderListPage() {
        return "orders/orderList";
    }
}
