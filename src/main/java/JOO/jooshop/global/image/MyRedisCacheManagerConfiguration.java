package JOO.jooshop.global.Images;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.time.Duration;

// MyRedisCacheManager 를 구성하는 Configuration 클래스입니다.
public class MyRedisCacheManagerConfiguration {

    // RedisCacheManagerBuilder 를 커스터마이징하는 빈을 제공합니다.
    // 이를 통해 "profileImages"라는 캐시의 라이프사이클을 60분으로 설정합니다.
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .withCacheConfiguration("profileImages", RedisCacheConfiguration
                        .defaultCacheConfig().entryTtl(Duration.ofMinutes(60)));
    }
}
