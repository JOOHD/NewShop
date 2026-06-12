package JOO.jooshop.global.authentication.jwts.handler;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.TokenCookieWriter;
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
 *
 * CustomUserDetails에서 memberId/role을 직접 꺼내기 때문에
 * authorities가 비어있을 때 ROLE_USER로 fallback되던 취약점이 제거됨.
 * MemberAccountService.findMemberByEmail() 추가 DB 조회도 제거됨.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberAccountService memberAccountService;
    private final TokenService tokenService;
    private final TokenCookieWriter tokenCookieWriter;

    @Value("${spring.backend.url}")
    private String backendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        // CustomUserDetails에서 직접 추출 — DB 재조회 없이 memberId/role 획득
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Long memberId = userDetails.getMemberId();
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElseThrow(() -> new IllegalStateException("인증된 사용자에 권한이 없습니다. memberId=" + memberId));

        Member member = memberAccountService.findMemberById(memberId);

        TokenResponse tokenResponse = tokenService.issueLoginTokens(member, role);
        tokenCookieWriter.write(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());

        log.info("폼 로그인 성공. memberId={}", memberId);
        getRedirectStrategy().sendRedirect(request, response, backendUrl + "/");
    }
}
