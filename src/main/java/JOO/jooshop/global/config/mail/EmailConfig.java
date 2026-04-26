package JOO.jooshop.global.config.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class EmailConfig {

    @Value("${spring.mail.username}")
    private String username; // 네이버 아이디
    @Value("${spring.mail.password}")
    private String password; // 네이버 비밀번호
    @Value("${spring.mail.host}")
    private String host; // SMTP 서버 주소
    @Value("${spring.mail.port}")
    private int port; // SMTP 서버 포트

    @Bean
    public JavaMailSender javaMailService() {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();

        javaMailSender.setHost(host); // 메인 도메인 서버 주소 => 정확히는 smtp 서버 주소
        javaMailSender.setPort(port);
        javaMailSender.setUsername(username); // 네이버 아이디
        javaMailSender.setPassword(password); // 네이버 비밀번호
        javaMailSender.setDefaultEncoding("UTF-8");
        javaMailSender.setJavaMailProperties(getMailProperties()); // 메일 인증서버 정보 가져오기

        return javaMailSender;
    }

    private Properties getMailProperties() {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", true); // smtp 인증
        properties.put("mail.smtp.starttls.enable", true); // smtp strattles 사용
        properties.put("mail.smtp.starttls.required", true);
        properties.put("mail.smtp.connectiontimeout", 5000);
        properties.put("mail.smtp.timeout", 5000);
        properties.put("mail.smtp.writetimeout", 5000);

//        properties.setProperty("mail.transport.protocol", "smtp"); // 프로토콜 설정
        properties.setProperty("mail.debug", "true"); // 디버그 사용
        properties.setProperty("mail.smtp.ssl.trust","smtp.naver.com"); // ssl 인증 서버는 smtp.naver.com
        properties.setProperty("mail.smtp.ssl.enable","true"); // ssl 사용
        return properties;
    }
}
