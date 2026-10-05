package JOO.jooshop.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;
    @Value("${spring.data.redis.port}")
    private int port;

    // Redis 연결 팩토리 생성
    @Bean // Redis 연결 메서드
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(host, port);
    }

    // Redis 템플릿 설정 (키/값 직렬화 방식 지정)
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        /*
            1. Redis에서는 객체를 그대로 저장할 수 없기 때문에, 객체를 Redis에 저장 가능한 JSON으로 직렬화
                ex) 객체 → 직렬화 → Redis 저장
                    // Jav 객체
                       User user = new User("john", "john@example.com")
                    // JSON 문자열로 직렬화
                       String serialized = objectMapper.writeValueAsString(user);
                    // Redis 저장
                       redisTemplate.opsForValue().set("USER:john", serialized);

            2. Redis에서 값을 꺼낼 때는 다시 객체로 역직렬화(deserialize) 해서 사용합니다.
                ex) Redis -> 역직렬화 -> 객체
                    // Redis 에서 JSON 문자열 꺼내기
                       String serialized = redistTemplate.opsForValue().get("USER:john");

                    // JSON 문자열을 다시 User 객체로 역직렬화
                       User user = objectMapper.readValue(serialized, User.class);

            3. Key/Value를 문자열 또는 JSON 형식으로 변환해 Redis에 저장 가능하게 함.
                ex) {prefix}:{identifier} -> REFRESH:member123 & 	BLACKLIST:{jti}

         */
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        // 직렬화 설정 (향후 DTO or Entity 일부를 Redis에 캐싱할 경우 유리함)
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer()); // 객체도 저장 가능
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
