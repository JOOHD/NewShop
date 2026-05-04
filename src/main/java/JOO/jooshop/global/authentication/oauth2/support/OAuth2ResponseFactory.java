package JOO.jooshop.global.authentication.oauth2.support;

import JOO.jooshop.global.authentication.oauth2.responsedto.GoogleResponse;
import JOO.jooshop.global.authentication.oauth2.responsedto.KakaoResponse;
import JOO.jooshop.global.authentication.oauth2.responsedto.NaverResponse;
import JOO.jooshop.global.authentication.oauth2.responsedto.OAuth2Response;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OAuth2ResponseFactory {

    public OAuth2Response create(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "naver" -> new NaverResponse(attributes);
            case "google" -> new GoogleResponse(attributes);
            case "kakao" -> new KakaoResponse(attributes);
            default -> throw new IllegalArgumentException("지원하지 않는 OAuth2 provider 입니다.");
        };
    }
}
