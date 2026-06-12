package JOO.jooshop.global.config.app;

import com.siot.IamportRestClient.IamportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * IamportClient Bean 설정.
 * API 키/시크릿은 환경 변수 또는 yml에서 주입.
 */
@Configuration
public class IamportConfig {

    @Value("${IMP_API_KEY}")
    private String apiKey;

    @Value("${IMP_SECRET_KEY}")
    private String secretKey;

    @Bean
    public IamportClient iamportClient() {
        return new IamportClient(apiKey, secretKey);
    }
}
