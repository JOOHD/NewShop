package JOO.jooshop.global.authentication.oauth2.controller;

import JOO.jooshop.global.authentication.jwts.utils.TokenCookieWriter;
import JOO.jooshop.global.authentication.oauth2.dto.SocialTokenResponse;
import JOO.jooshop.global.authentication.oauth2.service.OAuth2LoginService;
import JOO.jooshop.global.authentication.oauth2.service.OAuth2LoginService.KakaoLoginResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * OAuth2 로그인 진입점 컨트롤러.
 * 토큰은 HttpOnly 쿠키로 설정하고, 바디에는 email만 내려준다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/socialLogin")
public class OAuth2LoginController {

    private final OAuth2LoginService oAuth2LoginService;
    private final TokenCookieWriter tokenCookieWriter;

    @GetMapping("/authorization/kakao")
    public void redirectKakaoAuthorization(HttpServletResponse response) throws IOException {
        String authorizationUrl = oAuth2LoginService.createKakaoAuthorizationUrl();
        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/login/oauth2/code/kakao")
    public ResponseEntity<SocialTokenResponse> kakaoCallback(
            @RequestParam String code,
            HttpServletResponse response
    ) {
        KakaoLoginResult result = oAuth2LoginService.loginWithKakao(code);

        // 토큰은 쿠키로
        tokenCookieWriter.write(
                response,
                result.tokenResponse().getAccessToken(),
                result.tokenResponse().getRefreshToken()
        );

        // 바디에는 email만
        return ResponseEntity.ok(SocialTokenResponse.of(result.email()));
    }
}