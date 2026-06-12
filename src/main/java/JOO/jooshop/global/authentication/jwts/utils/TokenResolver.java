package JOO.jooshop.global.authentication.jwts.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * HttpServletRequest 에서 쿠키 또는 Authorization header 에 담긴 토큰 추출
 */
// @Component 생성자를 막고, 정적 메서드만 쓰는 것은 모순이다.
public final class TokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";
    
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
                .findFirst();
    }
}
