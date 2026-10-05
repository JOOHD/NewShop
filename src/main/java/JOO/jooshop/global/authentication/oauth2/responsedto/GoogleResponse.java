package JOO.jooshop.global.authentication.oauth2.responsedto;

import java.util.Map;

public class GoogleResponse implements OAuth2Response {

    private static final String PROVIDER = "google";

    private final Map<String, Object> attributes;

    // 구글 사용자 정보 응답 보관
    public GoogleResponse(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    // 소셜 제공자 이름
    @Override
    public String getProvider() {
        return PROVIDER;
    }

    // 구글 사용자 고유 ID (필수)
    @Override
    public String getProviderId() {
        return getRequiredValue("sub");
    }

    // 구글 계정 이메일
    @Override
    public String getEmail() {
        return getNullableValue("email");
    }

    // 사용자 이름
    @Override
    public String getName() {
        return getNullableValue("name");
    }

    // 필수 값 조회 (없으면 예외)
    private String getRequiredValue(String key) {
        String value = getNullableValue(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("구글 OAuth2 응답에 필수 값이 없습니다. key=" + key);
        }

        return value;
    }

    // 값 조회 (없으면 null)
    private String getNullableValue(String key) {
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }
}