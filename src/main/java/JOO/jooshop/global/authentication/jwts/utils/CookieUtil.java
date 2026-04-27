package JOO.jooshop.global.authentication.jwts.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 인증 쿠키 생성, 조회, 삭제 유틸 클래스.
 */
public final class CookieUtil {

    private CookieUtil() {
    }

    public static void addSecureCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        addCookie(response, name, value, maxAgeSeconds, true, "None");
    }

    public static void addLocalCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        addCookie(response, name, value, maxAgeSeconds, false, "Lax");
    }

    public static void deleteSecureCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", 0, true, "None");
    }

    public static void deleteLocalCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", 0, false, "Lax");
    }

    public static String getCookieValue(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private static void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            int maxAgeSeconds,
            boolean secure,
            String sameSite
    ) {
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);

        StringBuilder cookieBuilder = new StringBuilder()
                .append(name).append("=").append(encodedValue)
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; Path=/")
                .append("; HttpOnly")
                .append("; SameSite=").append(sameSite);

        if (secure) {
            cookieBuilder.append("; Secure");
        }

        response.addHeader("Set-Cookie", cookieBuilder.toString());
    }
}