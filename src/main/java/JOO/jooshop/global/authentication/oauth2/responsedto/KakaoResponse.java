package JOO.jooshop.global.authentication.oauth2.responsedto;

import java.util.Map;

public class KakaoResponse implements OAuth2Response {

    private static final String PROVIDER = "kakao";

    private final Map<String, Object> attributes;

    public KakaoResponse(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public String getProviderId() {
        return getRequiredValue("id");
    }

    @Override
    public String getEmail() {
        Map<?, ?> kakaoAccount = getMap(attributes.get("kakao_account"));

        if (kakaoAccount == null) {
            return null;
        }

        Object email = kakaoAccount.get("email");
        return email == null ? null : email.toString();
    }

    @Override
    public String getName() {
        String nicknameFromAccountProfile = getNicknameFromKakaoAccountProfile();

        if (nicknameFromAccountProfile != null) {
            return nicknameFromAccountProfile;
        }

        String nicknameFromProperties = getNicknameFromProperties();

        if (nicknameFromProperties != null) {
            return nicknameFromProperties;
        }

        return "kakao_user_" + getProviderId();
    }

    private String getNicknameFromKakaoAccountProfile() {
        Map<?, ?> kakaoAccount = getMap(attributes.get("kakao_account"));

        if (kakaoAccount == null) {
            return null;
        }

        Map<?, ?> profile = getMap(kakaoAccount.get("profile"));

        if (profile == null) {
            return null;
        }

        Object nickname = profile.get("nickname");
        return nickname == null ? null : nickname.toString();
    }

    private String getNicknameFromProperties() {
        Map<?, ?> properties = getMap(attributes.get("properties"));

        if (properties == null) {
            return null;
        }

        Object nickname = properties.get("nickname");
        return nickname == null ? null : nickname.toString();
    }

    private String getRequiredValue(String key) {
        Object value = attributes.get(key);

        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("카카오 OAuth2 응답에 필수 값이 없습니다. key=" + key);
        }

        return value.toString();
    }

    private Map<?, ?> getMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }

        return null;
    }
}