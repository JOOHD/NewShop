package JOO.jooshop.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration // spring 설정 클래스임을 명시
public class WebConfig implements WebMvcConfigurer {

    // JAR로 빌드하면 classpath(resources/static)는 읽기 전용이라 런타임에 파일을 못 씀 —
    // 그래서 업로드 파일은 JAR 바깥의 별도 경로(file.upload-dir)에 저장/서빙한다.
    // 로컬은 기본값(프로젝트 루트의 uploads/)을 쓰고, 운영은 application.yml/환경변수로 실제 경로를 주입.
    @Value("${file.upload-dir:uploads/}")
    private String uploadDir;

    // /uploads/** 요청을 업로드 폴더의 실제 파일로 연결
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = new File(uploadDir).getAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}

