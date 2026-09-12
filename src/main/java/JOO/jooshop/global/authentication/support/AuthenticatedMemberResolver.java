package JOO.jooshop.global.authentication.support;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authentication.oauth2.custom.entity.CustomOAuth2User;
import org.springframework.security.core.Authentication;

/**
 * 폼 로그인(JWT 기반, principal=CustomUserDetails)과
 * 소셜 로그인(세션 기반, principal=CustomOAuth2User) 두 인증 경로가
 * 서로 다른 Principal 타입을 쓰기 때문에,
 * "@AuthenticationPrincipal CustomUserDetails"로 직접 캐스팅하면
 * 소셜 로그인 사용자는 항상 null(NPE)이 되는 문제가 있었다.
 *
 * 화면 컨트롤러에서 로그인 방식에 상관없이 memberId만 안전하게 꺼내 쓰기 위한 헬퍼.
 */
public final class AuthenticatedMemberResolver {

    private AuthenticatedMemberResolver() {
    }

    public static Long resolveMemberId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getMemberId();
        }

        if (principal instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getMemberId();
        }

        throw new IllegalStateException("지원하지 않는 Principal 타입입니다: " + principal.getClass());
    }
}
