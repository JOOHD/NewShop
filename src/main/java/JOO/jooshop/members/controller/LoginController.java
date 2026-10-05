package JOO.jooshop.members.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 로그인 페이지 뷰 컨트롤러.
 *
 * POST /formLogin → Spring Security Form Login → FormLoginSuccessHandler → JWT 쿠키 발급
 * OAuth2 로그인  → Spring Security OAuth2    → OAuth2LoginSuccessHandler → JWT 쿠키 발급
 */
@Controller
public class LoginController {

    // 로그인 화면 — 이미 로그인 상태면 메인으로 이동
    @GetMapping("/login")
    public String loginPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/";
        }
        return "members/login";
    }
}
