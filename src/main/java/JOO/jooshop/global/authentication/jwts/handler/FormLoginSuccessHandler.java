package JOO.jooshop.global.authentication.jwts.handler;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Form Login 성공 시 JWT 발급 및 쿠키 저장을 처리하는 Handler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final int ACCESS_COOKIE_MAX_AGE_SECONDS = 60 * 30;
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 14;

    private final MemberAccountService memberAccountService;
    private final TokenService tokenService;

    @Value("${spring.backend.url}")
    private String backendUrl;

    @Value("${app.secure:false}")
    private boolean secureCookie;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        String email = authentication.getName();
        Member member = memberAccountService.findMemberByEmail(email);

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElse("ROLE_USER");

        TokenResponse tokenResponse = tokenService.issueLoginTokens(member, role);

        addTokenCookies(response, tokenResponse);

        log.info("폼 로그인 성공. memberId={}", member.getId());

        getRedirectStrategy().sendRedirect(request, response, backendUrl + "/");
    }

    private void addTokenCookies(HttpServletResponse response, TokenResponse tokenResponse) {
        if (secureCookie) {
            CookieUtil.addSecureCookie(response, "accessToken", tokenResponse.getAccessToken(), ACCESS_COOKIE_MAX_AGE_SECONDS);
            CookieUtil.addSecureCookie(response, "refreshAuthorization", tokenResponse.getRefreshToken(), REFRESH_COOKIE_MAX_AGE_SECONDS);
            return;
        }

        CookieUtil.addLocalCookie(response, "accessToken", tokenResponse.getAccessToken(), ACCESS_COOKIE_MAX_AGE_SECONDS);
        CookieUtil.addLocalCookie(response, "refreshAuthorization", tokenResponse.getRefreshToken(), REFRESH_COOKIE_MAX_AGE_SECONDS);
    }
}