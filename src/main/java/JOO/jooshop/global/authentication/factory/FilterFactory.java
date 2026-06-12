package JOO.jooshop.global.authentication.factory;

import JOO.jooshop.global.authentication.jwts.filter.JWTFilterV3;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring Security 필터 생성 팩토리.
 * 로그인은 Spring Security 기본 Form Login / OAuth2가 처리하므로
 * 이 팩토리는 JWT 검증 필터만 생성한다.
 */
@Component
@RequiredArgsConstructor
public class FilterFactory {

    private final ObjectMapper objectMapper;
    private final JWTUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    public JWTFilterV3 createJWTFilter() {
        return new JWTFilterV3(jwtUtil, redisTemplate, objectMapper);
    }
}
