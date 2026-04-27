package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;

/**
 * 로그인 성공/실패 후 응답을 처리하는 필터.
 * 실제 토큰 발급/저장은 TokenService에 위임한다.
 */
@Slf4j
public class LoginFilter extends CustomJsonEmailPasswordAuthenticationFilter {

    private static final int ACCESS_COOKIE_MAX_AGE_SECONDS = 60 * 30;
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 14;

    private final MemberAccountService memberAccountService;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final boolean secureCookie;

    public LoginFilter(
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper,
            MemberAccountService memberAccountService,
            TokenService tokenService,
            @Value("${app.secure:false}") boolean secureCookie
    ) {
        super(authenticationManager, objectMapper);
        this.objectMapper = objectMapper;
        this.memberAccountService = memberAccountService;
        this.tokenService = tokenService;
        this.secureCookie = secureCookie;
    }

    @Override
    protected void successfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain,
            Authentication authentication
    ) throws IOException, ServletException {

        String email = authentication.getName();
        Member member = memberAccountService.findMemberByEmail(email);

        validateCertifiedMember(member);

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElseThrow(() -> new AuthenticationServiceException("권한 정보가 없습니다."));

        TokenResponse tokenResponse = tokenService.issueLoginTokens(member, role);

        addTokenCookies(response, tokenResponse);
        writeTokenResponse(response, tokenResponse);

        log.info("로그인 성공. memberId={}", member.getId());
    }

    @Override
    protected void unsuccessfulAuthentication(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failed
    ) throws IOException, ServletException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(response.getWriter(), java.util.Map.of(
                "error", "LOGIN_FAILED",
                "message", failed.getMessage()
        ));

        log.warn("로그인 실패: {}", failed.getMessage());
    }

    private void validateCertifiedMember(Member member) {
        if (!member.isCertifiedByEmail()) {
            throw new AuthenticationServiceException("이메일 인증이 필요합니다.");
        }
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

    private void writeTokenResponse(HttpServletResponse response, TokenResponse tokenResponse) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), tokenResponse);
    }
}