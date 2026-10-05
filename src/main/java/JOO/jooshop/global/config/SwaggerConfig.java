package JOO.jooshop.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(OpenAPI) 문서 설정.
 * 접속 경로: /swagger-ui/index.html (SecurityConfig에서 별도 인증 없이 접근 가능)
 */
@Configuration
public class SwaggerConfig {

    // Swagger(OpenAPI) 문서 설정
    @Bean
    public OpenAPI jooShopOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JooShop API")
                        .description("Spring Boot 기반 쇼핑몰 백엔드 API 문서입니다. "
                                + "인증(JWT/OAuth2), 상품, 장바구니, 주문/결제, 관리자 기능을 포함합니다.")
                        .version("v1"));
    }
}
