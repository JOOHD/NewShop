package JOO.jooshop.global.config.security;

import org.springframework.http.HttpMethod;

/**
 * Spring Security 인가 정책에서 사용하는 URL 경로를 관리하는 클래스입니다.
 *
 * 목적:
 * - SecurityConfig 내부의 URL 배열을 분리하여 가독성을 높입니다.
 * - 공개 API, 사용자 API, 관리자 API 경로를 한 곳에서 관리합니다.
 * - 컨트롤러 URL 변경 시 보안 정책 누락을 줄입니다.
 */
public final class SecurityPath {

    private SecurityPath() {
    }

    /**
     * 인증 없이 접근 가능한 API 경로입니다.
     */
    public static final String[] PUBLIC_API = {
            "/api/join",
            "/api/admin/join",
            "/api/verify",
            "/api/email/**",
            "/api/v1/categorys/**",
            "/api/v1/thumbnail/**",
            "/api/v1/members/join",
            "/api/v1/members/check-email",
            "/api/v1/reissue/**",
            "/api/v1/inquiry/**",
            "/api/socialLogin/**"
    };

    /**
     * 일반 사용자 또는 판매자 권한이 필요한 API 경로입니다.
     */
    public static final String[] USER_OR_SELLER_API = {
            "/api/v1/profile/**",
            "/api/v1/cart/**",
            "/api/v1/order/**",
            "/api/v1/product/**",
            "/api/v1/payment/**"
    };

    /**
     * 관리자 권한이 필요한 API 경로입니다.
     */
    public static final String[] ADMIN_API = {
            "/api/v1/admin/products/**",
            "/api/v1/admin/orders/**",
            "/api/v1/admin/members/**",
            "/admin/members/**",
            "/api/v1/inventory/**",
            "/api/v1/inquiry/reply/**"
    };

    /**
     * 인증 없이 조회 가능한 상품 API입니다.
     *
     * 주의:
     * - 실제 ProductController 경로와 반드시 맞춰야 합니다.
     */
    public static final String[] PUBLIC_GET_API = {
            "/api/v1/product/**"
    };

    /**
     * 인증 없이 접근 가능한 Web 경로입니다.
     */
    public static final String[] PUBLIC_WEB = {
            "/",
            "/login",
            "/formLogin",
            "/logout",
            "/oauth2/**",
            "/login/oauth2/**",
            "/auth/**",
            "/products/**"
    };

    /**
     * 로그인 사용자가 접근 가능한 Web 경로입니다.
     */
    public static final String[] AUTHENTICATED_WEB = {
            "/profile"
    };

    /**
     * 관리자 권한이 필요한 Web 경로입니다.
     */
    public static final String[] ADMIN_WEB = {
            "/admin"
    };
}