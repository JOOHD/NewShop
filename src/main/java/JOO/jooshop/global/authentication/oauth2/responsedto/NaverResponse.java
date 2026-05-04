package JOO.jooshop.global.authentication.oauth2.responsedto;

import java.util.Map;

public class NaverResponse implements OAuth2Response {

    private static final String PROVIDER = "naver";

    private final Map<String, Object> attributes;

    @SuppressWarnings("unchecked")
    public NaverResponse(Map<String, Object> attribute) {
        // "response" 키에 해당하는 값이 Map<String, Object> 타입인지 확인
        Object response = attribute.get("response");

        if (!(response instanceof Map<?,?> responseMap)) {
            throw new IllegalArgumentException("네이버 OAuth2 응답에 response 값이 없습니다.");
        }

        this.attributes = (Map<String, Object>) responseMap;
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
        return getNullableValue("email");
    }

    @Override
    public String getName() {
        String name = getNullableValue("name");

        if (name != null) {
            return name;
        }

        return getNullableValue("nickname");
    }

    private String getRequiredValue(String key) {
        String value = getNullableValue(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("네이버 OAuth2 응답에 필수 값이 없습니다. key=" + key);
        }

        return value;
    }

    private String getNullableValue(String key) {
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }
}
