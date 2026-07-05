package JOO.jooshop.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 설정
 *
 * RestTemplate과 달리 WebClient는 Bean으로 등록해서 재사용.
 * → 커넥션 풀을 공유해서 카카오 서버와의 연결을 효율적으로 관리.
 *
 * maxInMemorySize: 응답 바디 버퍼 최대 크기 (기본 256KB → 2MB로 확장)
 * → 카카오 프로필 응답 등 큰 JSON을 받을 때 BufferOverflowException 방지.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(config ->
                        config.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)
                )
                .build();
    }
}
