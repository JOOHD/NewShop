package JOO.jooshop.global.authentication.oauth2.handler;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.TokenCookieWriter;
import JOO.jooshop.global.authentication.oauth2.custom.entity.CustomOAuth2User;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberAccountService memberAccountService;
    private final TokenService tokenService;
    private final TokenCookieWriter tokenCookieWriter;

    @Value("${spring.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        CustomOAuth2User oAuth2User = extractPrincipal(authentication);
        Member member = memberAccountService.findMemberBySocialId(oAuth2User.getSocialId());

        String role = extractRole(authentication);
        TokenResponse tokenResult = tokenService.issueLoginTokens(member, role);

        tokenCookieWriter.write(response, tokenResult.getAccessToken(), tokenResult.getRefreshToken());

        log.info("[OAuth2] 로그인 성공. memberId={}, email={}", member.getId(), member.getEmail());
        response.sendRedirect(frontendUrl + "/login?redirectedFromSocialLogin=true");
    }

    private CustomOAuth2User extractPrincipal(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (!(principal instanceof CustomOAuth2User customOAuth2User)) {
            throw new IllegalStateException(
                    "OAuth2 Principal 타입이 올바르지 않습니다. principal=" + principal.getClass()
            );
        }

        return customOAuth2User;
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElseThrow(() -> new IllegalStateException("OAuth2 사용자 권한이 존재하지 않습니다."));
    }
}