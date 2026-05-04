package JOO.jooshop.global.authentication.oauth2.dto;

import lombok.Getter;

/**
 * REST 방식 카카오 로구인에서 클라이언트에게 내려줄 응답 DTO
 */
@Getter
public class SocialTokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String email;

    private SocialTokenResponse(String accessToken, String refreshToken, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.email = email;
    }

    public static SocialTokenResponse of(String accessToken, String refreshToken, String email) {
        return new SocialTokenResponse(accessToken, refreshToken, email);
    }
}