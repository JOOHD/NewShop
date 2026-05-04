package JOO.jooshop.global.authentication.oauth2.dto;

import lombok.Getter;

@Getter
public class OAuth2TokenResult {

    private final String accessToken;
    private final String refreshToken;

    private OAuth2TokenResult(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static OAuth2TokenResult of(String accessToken, String refreshToken) {
        return new OAuth2TokenResult(accessToken, refreshToken);
    }
}
