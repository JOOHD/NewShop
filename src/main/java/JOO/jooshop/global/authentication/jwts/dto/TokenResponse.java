package JOO.jooshop.global.authentication.jwts.dto;

import lombok.Getter;

/**
 * 일반 로그인 / OAuth2 로그인 공통 토큰 응답 DTO.
 * AccessToken, RefreshToken을 담아 반환한다.
 * OAuth2TokenResult를 대체한다.
 */
@Getter
public class TokenResponse {

    private final String accessToken;
    private final String refreshToken;

    private TokenResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static TokenResponse of(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken);
    }
}