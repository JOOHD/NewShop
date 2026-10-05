package JOO.jooshop.global.authentication.oauth2.dto;

import lombok.Getter;

/**
 * REST 방식 카카오 로그인 후, 클라이언트에 내려줄 응답 DTO
 * 토큰은 HttpOnly 쿠키로 전달되므로 바디에는 email만 포함된다.
 * 프론트가 로그이 후, 사용자 식별에 필요한 최소 정보만 제공한다.
 */
@Getter
public class SocialTokenResponse {

    private final String email;

    // 소셜 로그인 응답 생성자
    private SocialTokenResponse(String email) {
        this.email = email;
    }

    // 이메일로 소셜 로그인 응답 생성
    public static SocialTokenResponse of(String email) {
        return new SocialTokenResponse(email);
    }
}