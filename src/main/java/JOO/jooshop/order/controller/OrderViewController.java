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
    // @AuthenticationPrincipal CustomUserDetails로 직접 캐스팅하지 않고
    // AuthenticatedMemberResolver로 memberId만 안전하게 꺼낸다.
    @GetMapping("/tempOrder")
    public String tempOrderPage(Authentication authentication, Model model) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        model.addAttribute("memberId", memberId);
        return "orders/tempOrder";
    }

    @GetMapping("/order")
    public String orderPage(Authentication authentication, Model model) {
        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        Member member = memberAccountService.findMemberById(memberId);

        model.addAttribute("username", member.getUsername());
        model.addAttribute("phoneNumber", member.getPhoneNumber());

        return "orders/order";
    }
}
