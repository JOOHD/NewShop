package JOO.jooshop.global.authentication.oauth2.handler;

import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import JOO.jooshop.global.authentication.oauth2.custom.entity.CustomOAuth2User;
import JOO.jooshop.global.authentication.oauth2.dto.OAuth2TokenResult;
import JOO.jooshop.global.authentication.oauth2.service.OAuth2TokenService;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final int ACCESS_TOKEN_COOKIE_MAX_AGE_SECONDS = 900;
    private static final int REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS = 1_209_600;

    private final MemberRepository memberRepository;
    private final OAuth2TokenService oAuth2TokenService;

    @Value("${spring.frontend.url}")
    private String frontendUrl;

    @Value("${app.secure}")
    private boolean secure;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        CustomOAuth2User oAuth2User = extractPrincipal(authentication);

        Member member = memberRepository.findBySocialId(oAuth2User.getSocialId())
                .orElseThrow(() -> new UsernameNotFoundException("해당 socialId를 가진 회원이 존재하지 않습니다."));

        String role = extractRole(authentication);

        OAuth2TokenResult tokenResult = oAuth2TokenService.issueToken(member, role);

        addTokenCookies(response, tokenResult);

        log.info("[OAuth2] 로그인 성공. memberId={}, email={}", member.getId(), member.getEmail());

        response.sendRedirect(frontendUrl + "/login?redirectedFromSocialLogin=true");
    }

    private CustomOAuth2User extractPrincipal(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (!(principal instanceof CustomOAuth2User customOAuth2User)) {
            throw new IllegalStateException("OAuth2 Principal 타입이 올바르지 않습니다. principal=" + principal.getClass());
        }

        return customOAuth2User;
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElseThrow(() -> new IllegalStateException("OAuth2 사용자 권한이 존재하지 않습니다."));
    }

    private void addTokenCookies(HttpServletResponse response, OAuth2TokenResult tokenResult) {
        if (secure) {
            CookieUtil.createCookieWithSameSite(
                    response,
                    "accessToken",
                    tokenResult.getAccessToken(),
                    ACCESS_TOKEN_COOKIE_MAX_AGE_SECONDS
            );

            CookieUtil.createCookieWithSameSite(
                    response,
                    "refreshAuthorization",
                    tokenResult.getRefreshToken(),
                    REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS
            );
            return;
        }

        CookieUtil.createCookieWithSameSiteForLocal(
                response,
                "accessToken",
                tokenResult.getAccessToken(),
                ACCESS_TOKEN_COOKIE_MAX_AGE_SECONDS
        );

        CookieUtil.createCookieWithSameSiteForLocal(
                response,
                "refreshAuthorization",
                tokenResult.getRefreshToken(),
                REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS
        );
    }
}