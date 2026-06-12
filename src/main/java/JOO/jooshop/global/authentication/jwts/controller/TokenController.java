package JOO.jooshop.global.authentication.jwts.controller;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import JOO.jooshop.global.authentication.jwts.utils.TokenCookieWriter;
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

    private final TokenService tokenService;
    private final TokenCookieWriter tokenCookieWriter;

    @PostMapping("/api/v1/reissue")
    public ResponseEntity<TokenResponse> reissue(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = TokenResolver.resolveTokenFromCookie(request, "refreshAuthorization")
                .orElseThrow(() -> new IllegalArgumentException("RefreshToken이 존재하지 않습니다."));

        TokenResponse tokenResponse = tokenService.reissue(refreshToken);

        tokenCookieWriter.write(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());

        return ResponseEntity.ok(tokenResponse);
    }
}