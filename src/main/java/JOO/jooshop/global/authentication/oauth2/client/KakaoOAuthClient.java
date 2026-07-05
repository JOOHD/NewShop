package JOO.jooshop.global.authentication.oauth2.client;

import JOO.jooshop.global.authentication.oauth2.dto.KakaoProfileResponse;
import JOO.jooshop.global.authentication.oauth2.dto.OAuthTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 카카오 OAuth2 API 클라이언트
 *
 * [RestTemplate → WebClient 전환 이유]
 * RestTemplate: 동기(Blocking) - 카카오 응답 올 때까지 Thread가 대기. Spring 6 deprecated.
 * WebClient:    비동기(Non-Blocking) - Thread가 콜백만 등록하고 반납. 공식 권장.
 *
 * 현재 MVC 환경이라 .block()으로 동기처럼 쓰지만, WebClient를 쓰는 이유:
 * 1. .onStatus()로 4xx/5xx 에러를 명확하게 분리 처리
 * 2. Bean으로 등록 → 커넥션 풀 재사용 (RestTemplate은 new RestTemplate() 매번 생성)
 * 3. deprecated 아님 → 장기 유지보수 안전
 * 4. 추후 완전 비동기 전환 시 .block()만 제거하면 됨
 */
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

    private static final String KAKAO_AUTHORIZATION_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String KAKAO_TOKEN_URI         = "https://kauth.kakao.com/oauth/token";
    private static final String KAKAO_USER_INFO_URI     = "https://kapi.kakao.com/v2/user/me";

    private final WebClient webClient;  // Bean 주입 → 커넥션 풀 재사용

    @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
    private String kakaoClientId;

    @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
    private String kakaoRedirectUri;

    /**
     * 카카오 인증 페이지 URL 생성
     * → 프론트에서 이 URL로 리다이렉트하면 카카오 로그인 화면으로 이동
     */
    public String createAuthorizationUrl() {
        return KAKAO_AUTHORIZATION_URI
                + "?client_id=" + kakaoClientId
                + "&redirect_uri=" + kakaoRedirectUri
                + "&response_type=code";
    }

    /**
     * Authorization Code → 카카오 AccessToken 교환 (서버-서버 통신)
     *
     * [WebClient 흐름]
     * webClient.post()                  → POST 요청
     * .uri(KAKAO_TOKEN_URI)             → 카카오 토큰 발급 엔드포인트
     * .contentType(FORM_URLENCODED)     → form 형식으로 전송
     * .bodyValue(params)                → grant_type, client_id, code 등
     * .retrieve()                       → 응답 수신 시작
     * .onStatus(4xx, ...)               → 4xx 에러 시 예외 변환
     * .onStatus(5xx, ...)               → 5xx 에러 시 예외 변환
     * .bodyToMono(OAuthTokenResponse)   → 응답 JSON → DTO 변환
     * .block()                          → MVC 환경에서 동기 처리 (비동기 결과 대기)
     */
    public OAuthTokenResponse requestAccessToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("redirect_uri", kakaoRedirectUri);
        params.add("code", code);

        return webClient.post()
                .uri(KAKAO_TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(params)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new IllegalStateException(
                                        "카카오 토큰 요청 실패 (4xx): " + body))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new IllegalStateException(
                                        "카카오 서버 오류 (5xx): " + body))
                )
                .bodyToMono(OAuthTokenResponse.class)
                .block();
    }

    /**
     * 카카오 AccessToken으로 사용자 프로필 조회
     *
     * [RestTemplate과 비교]
     * Before: HttpHeaders 객체 직접 생성 → HttpEntity 감싸기 → exchange() 호출 → 응답 파싱
     * After:  .header()로 한 줄 설정 → .retrieve()로 바로 응답 수신 → 자동 파싱
     */
    public KakaoProfileResponse requestProfile(String accessToken) {
        return webClient.get()
                .uri(KAKAO_USER_INFO_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new IllegalStateException(
                                        "카카오 프로필 조회 실패 (4xx): " + body))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .map(body -> new IllegalStateException(
                                        "카카오 서버 오류 (5xx): " + body))
                )
                .bodyToMono(KakaoProfileResponse.class)
                .block();
    }
}
