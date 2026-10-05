package JOO.jooshop.global.authorization;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.Builder;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

import java.util.LinkedHashMap;
import java.util.Map;

public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;

    // 기본 리졸버를 감싸고 인가 요청 커스터마이저를 등록
    public CustomAuthorizationRequestResolver(ClientRegistrationRepository repo, String authorizationRequestBaseUri) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(repo, authorizationRequestBaseUri);

        // 커스터마이저 등록
        this.defaultResolver.setAuthorizationRequestCustomizer(builder -> {
        });
    }

    // 인가 요청 생성 (기본 리졸버에 위임)
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return defaultResolver.resolve(request);
    }

    // 제공자 ID를 지정한 인가 요청 생성 (기본 리졸버에 위임)
    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return defaultResolver.resolve(request, clientRegistrationId);
    }
}
