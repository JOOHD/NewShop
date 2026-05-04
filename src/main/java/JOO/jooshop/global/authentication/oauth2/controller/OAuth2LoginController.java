package JOO.jooshop.global.authentication.oauth2.controller;

import JOO.jooshop.global.authentication.oauth2.dto.SocialTokenResponse;
import JOO.jooshop.global.authentication.oauth2.service.OAuth2LoginService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/socialLogin")
public class OAuth2LoginController {

    private final OAuth2LoginService oAuth2LoginService;

    @GetMapping("/authorization/kakao")
    public void redirectKakaoAuthorization(HttpServletResponse response) throws IOException {
        String authorizationUrl = oAuth2LoginService.createKakaoAuthorizationUrl();
        response.sendRedirect(authorizationUrl);
    }

    @GetMapping("/login/oauth2/code/kakao")
    public ResponseEntity<SocialTokenResponse> kakaoCallback(@RequestParam String code) {
        SocialTokenResponse response = oAuth2LoginService.loginWithKakao(code);
        return ResponseEntity.ok(response);
    }
}