package JOO.jooshop.global.authentication.oauth2.responsedto;

import java.util.Map;

public class NaverResponse implements OAuth2Response {

    private static final String PROVIDER = "naver";

    private final Map<String, Object> attributes;

    // 네이버 응답의 "response" 영역을 꺼내 보관 (없으면 예외)
    @SuppressWarnings("unchecked")
    public NaverResponse(Map<String, Object> attribute) {
        // "response" 키에 해당하는 값이 Map<String, Object> 타입인지 확인
        Object response = attribute.get("response");

        if (!(response instanceof Map<?,?> responseMap)) {
            throw new IllegalArgumentException("네이버 OAuth2 응답에 response 값이 없습니다.");
        }

        this.attributes = (Map<String, Object>) responseMap;
    }

    // 소셜 제공자 이름
    @Override
    public String getProvider() {
        return PROVIDER;
    }

    // 네이버 사용자 고유 ID (필수)
    @Override
    public String getProviderId() {
        return getRequiredValue("id");
    }

    // 네이버 계정 이메일 (없으면 null)
    @Override
    public String getEmail() {
        return getNullableValue("email");
    }

    // 사용자 이름
    @Override
    public String getName() {
        String name = getNullableValue("name");

        if (name != null) {
            return name;
        }

        return getNullableValue("nickname");
    }

    // 필수 값 조회 (없으면 예외)
    private String getRequiredValue(String key) {
        String value = getNullableValue(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("네이버 OAuth2 응답에 필수 값이 없습니다. key=" + key);
        }

        return value;
    }

    // 값 조회 (없으면 null)
    private String getNullableValue(String key) {
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }
}
