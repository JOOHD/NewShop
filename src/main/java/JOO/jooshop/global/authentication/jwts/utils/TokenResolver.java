package JOO.jooshop.global.authentication.jwts.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * HttpServletRequest에서 쿠키 또는 Authorization 헤더에 담긴 AccessToken을 추출한다.

 * 핵심 역할
 * Cookie에서 accessToken 추출
 * 또는 Authorization Header에서 Bearer Token 추출
 */
@Component
public final class TokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BEARER_COOKIE_PREFIX = "Bearer+";
    
    private TokenResolver() {} // 기본 생성자
    
    public static Optional<String> resolveTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }

    public static Optional<String> resolveTokenFromCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return Optional.empty();

        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .map(TokenResolver::removeCookieBearerPrefix)
                .findFirst();
    }

    private static String removeCookieBearerPrefix(String value) {
        if (value != null && value.startsWith(BEARER_COOKIE_PREFIX)) {
            return value.substring(BEARER_COOKIE_PREFIX.length());
        }
        return value;
    }
}
