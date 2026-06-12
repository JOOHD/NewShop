package JOO.jooshop.global.authentication.jwts.utils;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Access/Refresh Token을 HttpOnly 쿠키로 응답에 추가한다.
 * 환경(운영/로컬)에 따라 Secure 쿠키 여부를 자동 분기한다.
 * FormLoginSuccessHandler, OAuth2LoginSuccessHandler,
 * EmailVerificationController의 중복 쿠키 로직을 한 곳으로 통합한다.
 */
@Component
@RequiredArgsConstructor
public class TokenCookieWriter {

    private static final int ACCESS_MAX_AGE = 60 * 30;
    private static final int REFRESH_MAX_AGE = 60 * 60 * 24 * 14;
    private static final String ACCESS_COOKIE_NAME = "accessToken";
    private static final String REFRESH_COOKIE_NAME = "refreshAuthorization";

    @Value("${app.secure:false}")
    private boolean secureCookie;

    /**
     * Access/Refresh Token을 쿠키로 응답에 추가한다.
     */
    public void write(HttpServletResponse response, String accessToken, String refreshToken) {
        if (secureCookie) {
            CookieUtil.addSecureCookie(response, ACCESS_COOKIE_NAME, accessToken, ACCESS_MAX_AGE);
            CookieUtil.addSecureCookie(response, REFRESH_COOKIE_NAME, refreshToken, REFRESH_MAX_AGE);
        } else {
            CookieUtil.addLocalCookie(response, ACCESS_COOKIE_NAME, accessToken, ACCESS_MAX_AGE);
            CookieUtil.addLocalCookie(response, REFRESH_COOKIE_NAME, refreshToken, REFRESH_MAX_AGE);
        }
    }

    /**
     * 로그아웃 시 Access/Refresh Token 쿠키를 만료 처리한다.
     */
    public void clear(HttpServletResponse response) {
        if (secureCookie) {
            CookieUtil.deleteSecureCookie(response, ACCESS_COOKIE_NAME);
            CookieUtil.deleteSecureCookie(response, REFRESH_COOKIE_NAME);
        } else {
            CookieUtil.deleteLocalCookie(response, ACCESS_COOKIE_NAME);
            CookieUtil.deleteLocalCookie(response, REFRESH_COOKIE_NAME);
        }
    }
}