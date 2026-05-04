package JOO.jooshop.global.authentication.oauth2.client;

import JOO.jooshop.global.authentication.oauth2.dto.KakaoProfileResponse;
import JOO.jooshop.global.authentication.oauth2.dto.OAuthTokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private static final String KAKAO_AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final ObjectMapper objectMapper;

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String kakaoRedirectUri;

    public String createAuthorizationUrl() {
        return KAKAO_AUTHORIZATION_URI
                + "?client_id=" + kakaoClientId
                + "&redirect_uri=" + kakaoRedirectUri
                + "&response_type=code";
    }

    public OAuthTokenResponse requestAccessToken(String code) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = createFormHeaders();
        MultiValueMap<String, String> params = createTokenRequestParams(code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                KAKAO_TOKEN_URI,
                HttpMethod.POST,
                request,
                String.class
        );

        return parseTokenResponse(response.getBody());
    }

    public KakaoProfileResponse requestProfile(String accessToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = createProfileHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                KAKAO_USER_INFO_URI,
                HttpMethod.GET,
                request,
                String.class
        );

        return parseProfileResponse(response.getBody());
    }

    private HttpHeaders createFormHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private MultiValueMap<String, String> createTokenRequestParams(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", kakaoRedirectUri);
        params.add("code", code);

        return params;
    }

    private HttpHeaders createProfileHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private OAuthTokenResponse parseTokenResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, OAuthTokenResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카카오 AccessToken 응답 파싱 실패", e);
        }
    }

    private KakaoProfileResponse parseProfileResponse(String responseBody) {
        try {
            return objectMapper.readValue(responseBody, KakaoProfileResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카카오 사용자 정보 응답 파싱 실패", e);
        }
    }
}