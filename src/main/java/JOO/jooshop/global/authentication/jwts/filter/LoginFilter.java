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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.io.IOException;
import java.util.Map;

/**
 * API 로그인 요청을 처리하는 인증 필터.
 *
 * 역할:
 * - /api/login 요청에서 이메일/비밀번호 인증 시도
 * - 인증 성공 시 회원 상태 검증
 * - TokenService를 통해 Access Token / Refresh Token 발급
 * - 발급된 토큰을 Cookie에 저장
 * - 로그인 성공/실패 응답을 JSON으로 반환
 *
 * 리팩토링 방향:
 * - JWTUtil, RefreshTokenRepository를 직접 사용하지 않는다.
 * - 토큰 발급/저장 책임은 TokenService에 위임한다.
 * - 필터는 인증 요청/응답 처리에 집중한다.
 */
@Slf4j
public class LoginFilter extends CustomJsonEmailPasswordAuthenticationFilter {

    private static final int ACCESS_COOKIE_MAX_AGE_SECONDS = 60 * 30;
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 14;

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshAuthorization";

    private final ObjectMapper objectMapper;
    private final MemberAccountService memberAccountService;
    private final TokenService tokenService;
    private final boolean secureCookie;

    public LoginFilter(
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper,
            MemberAccountService memberAccountService,
            TokenService tokenService,
            boolean secureCookie
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

        String role = extractRole(authentication);

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

        log.warn("로그인 실패: {}", failed.getMessage());

        writeErrorResponse(
                response,
                HttpStatus.UNAUTHORIZED,
                "LOGIN_FAILED",
                failed.getMessage()
        );
    }

    private void validateCertifiedMember(Member member) {
        if (!member.isCertifiedByEmail()) {
            throw new AuthenticationServiceException("이메일 인증이 필요합니다.");
        }
    }

    private String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority())
                .orElseThrow(() -> new AuthenticationServiceException("권한 정보가 없습니다."));
    }

    private void addTokenCookies(HttpServletResponse response, TokenResponse tokenResponse) {
        if (secureCookie) {
            CookieUtil.addSecureCookie(
                    response,
                    ACCESS_TOKEN_COOKIE_NAME,
                    tokenResponse.getAccessToken(),
                    ACCESS_COOKIE_MAX_AGE_SECONDS
            );

            CookieUtil.addSecureCookie(
                    response,
                    REFRESH_TOKEN_COOKIE_NAME,
                    tokenResponse.getRefreshToken(),
                    REFRESH_COOKIE_MAX_AGE_SECONDS
            );

            return;
        }

        CookieUtil.addLocalCookie(
                response,
                ACCESS_TOKEN_COOKIE_NAME,
                tokenResponse.getAccessToken(),
                ACCESS_COOKIE_MAX_AGE_SECONDS
        );

        CookieUtil.addLocalCookie(
                response,
                REFRESH_TOKEN_COOKIE_NAME,
                tokenResponse.getRefreshToken(),
                REFRESH_COOKIE_MAX_AGE_SECONDS
        );
    }

    private void writeTokenResponse(
            HttpServletResponse response,
            TokenResponse tokenResponse
    ) throws IOException {

        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(response.getWriter(), tokenResponse);
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String message
    ) throws IOException {

        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", error,
                "message", message
        ));
    }
}