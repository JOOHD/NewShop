package JOO.jooshop.global.authentication.oauth2.responsedto;

import java.util.Map;

public class GoogleResponse implements OAuth2Response {

    private static final String PROVIDER = "google";

    private final Map<String, Object> attributes;

    public GoogleResponse(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public String getProviderId() {
        return getRequiredValue("sub");
    }

    @Override
    public String getEmail() {
        return getNullableValue("email");
    }

    @Override
    public String getName() {
        return getNullableValue("name");
    }

    private String getRequiredValue(String key) {
        String value = getNullableValue(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("구글 OAuth2 응답에 필수 값이 없습니다. key=" + key);
        }

        return value;
    }

    private String getNullableValue(String key) {
        Object value = attributes.get(key);
        return value == null ? null : value.toString();
    }
}