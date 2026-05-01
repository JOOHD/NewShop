package JOO.jooshop.global.authentication.jwts.controller;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import JOO.jooshop.global.authentication.jwts.utils.TokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RefreshToken 기반 JWT 재발급 API.
 * 토큰 검증/저장/갱신 로직은 TokenService에 위임한다.
 */
@RestController
@RequiredArgsConstructor
public class TokenController {

    private static final int ACCESS_COOKIE_MAX_AGE_SECONDS = 60 * 30;
    private static final int REFRESH_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 14;

    private final TokenService tokenService;

    @Value("${app.secure:false}")
    private boolean secureCookie;

    @PostMapping("/api/v1/reissue")
    public ResponseEntity<TokenResponse> reissue(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = TokenResolver.resolveTokenFromCookie(request, "refreshAuthorization")
                .orElseThrow(() -> new IllegalArgumentException("RefreshToken이 존재하지 않습니다."));

        TokenResponse tokenResponse = tokenService.reissue(refreshToken);

        addTokenCookies(response, tokenResponse);

        return ResponseEntity.ok(tokenResponse);
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