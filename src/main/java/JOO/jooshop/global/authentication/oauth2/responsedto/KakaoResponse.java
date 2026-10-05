package JOO.jooshop.global.authentication.oauth2.responsedto;

import java.util.Map;

public class KakaoResponse implements OAuth2Response {

    private static final String PROVIDER = "kakao";

    private final Map<String, Object> attributes;

    // 카카오 사용자 정보 응답 보관
    public KakaoResponse(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    // 소셜 제공자 이름
    @Override
    public String getProvider() {
        return PROVIDER;
    }

    // 카카오 회원번호 (필수)
    @Override
    public String getProviderId() {
        return getRequiredValue("id");
    }

    // 카카오 계정 이메일 (없으면 null)
    @Override
    public String getEmail() {
        Map<?, ?> kakaoAccount = getMap(attributes.get("kakao_account"));

        if (kakaoAccount == null) {
            return null;
        }

        Object email = kakaoAccount.get("email");
        return email == null ? null : email.toString();
    }

    // 닉네임 — 카카오 계정 프로필 우선, 없으면 properties
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

    // kakao_account.profile에서 닉네임 추출
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

    // properties에서 닉네임 추출
    private String getNicknameFromProperties() {
        Map<?, ?> properties = getMap(attributes.get("properties"));

        if (properties == null) {
            return null;
        }

        Object nickname = properties.get("nickname");
        return nickname == null ? null : nickname.toString();
    }

    // 필수 값 조회 (없으면 예외)
    private String getRequiredValue(String key) {
        Object value = attributes.get(key);

        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("카카오 OAuth2 응답에 필수 값이 없습니다. key=" + key);
        }

        return value.toString();
    }

    // Map 타입이면 반환, 아니면 null
    private Map<?, ?> getMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }

        return null;
    }
}