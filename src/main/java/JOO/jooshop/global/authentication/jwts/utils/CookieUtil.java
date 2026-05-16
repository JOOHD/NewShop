package JOO.jooshop.global.authentication.jwts.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * JWT를 HttpOnly Cookie에 저장하거나 요청 쿠키에서 읽고,
 * 로그아웃 시 만료 쿠키로 삭제한다.

 * 핵심 역할
 * AccessToken 쿠키 저장
 * AccessToken 쿠키 조회
 * 로그아웃 시 쿠키 삭제
 */
public final class CookieUtil {

    private CookieUtil() {
    }

    /**u
     * 운영 환경용 인증 쿠키 생성
     * - SameSite=None
     * - Secure=true
     * - HttpOnly=true
     */
    public static void addSecureCookie(
            HttpServletResponse response,
            String name,
            String value,
            int maxAgeSeconds
    ) {
        addCookie(response, name, value, maxAgeSeconds, true, "None");
    }

    /**
     * 로컬 개발 환경용 인증 쿠키 생성
     * - SameSite=Lax
     * - Secure=false
     * - HttpOnly=true
     */
    public static void addLocalCookie(
            HttpServletResponse response,
            String name,
            String value,
            int maxAgeSeconds
    ) {
        addCookie(response, name, value, maxAgeSeconds, false, "Lax");
    }

    /**
     * 운영 환경용 인증 쿠키 삭제
     */
    public static void deleteSecureCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", 0, true, "None");
    }

    /**
     * 로컬 환경용 인증 쿠키 삭제
     */
    public static void deleteLocalCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", 0, false, "Lax");
    }

    /**
     * 기존 SuccessHandler 코드 호환용 메서드
     * 운영 환경:
     * - Secure=true
     * - SameSite=None
     */
    public static void createCookieWithSameSite(
            HttpServletResponse response,
            String name,
            String value,
            int maxAgeSeconds
    ) {
        addSecureCookie(response, name, value, maxAgeSeconds);
    }

    /**
     * 기존 SuccessHandler 코드 호환용 메서드
     * 로컬 환경:
     * - Secure=false
     * - SameSite=Lax
     */
    public static void createCookieWithSameSiteForLocal(
            HttpServletResponse response,
            String name,
            String value,
            int maxAgeSeconds
    ) {
        addLocalCookie(response, name, value, maxAgeSeconds);
    }

    /**
     * 쿠키 값 조회
     */
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

    /**
     * Set-Cookie 헤더 직접 생성
     * Servlet Cookie 객체는 SameSite 속성을 직접 지원하지 않기 때문에
     * response.addHeader("Set-Cookie", ...) 방식으로 쿠키를 생성한다.
     */
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