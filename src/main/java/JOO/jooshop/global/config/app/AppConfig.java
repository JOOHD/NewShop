package JOO.jooshop.global.config.app;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    // ModelMapper 빈 등록
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }
}
