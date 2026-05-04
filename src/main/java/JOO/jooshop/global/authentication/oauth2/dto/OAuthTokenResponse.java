package JOO.jooshop.global.authentication.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OAuthTokenResponse {

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("id_token")
    private String idToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    private String scope;                       // 토큰 허용 범위

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("refresh_token_expires_in")
    private long refreshTokenExpiresIn;
}