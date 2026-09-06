package JOO.jooshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class JooshopApplication {
    public static void main(String[] args) {
        SpringApplication.run(JooshopApplication.class, args);
    }

    // MemberAccountService/AdminMemberService가 생성자에서 BCryptPasswordEncoder를 직접 요구하는데,
    // 이 Bean을 등록하는 곳이 어디에도 없어서 "No qualifying bean" 에러로 기동 자체가 실패했음.
    // import는 남아있었던 걸 보면 예전엔 여기 있다가 리팩토링 중 실수로 지워진 것으로 보임 — 복원.
    // 반환 타입을 BCryptPasswordEncoder(구체 타입)로 선언해야
    // PasswordEncoder(인터페이스)로 주입받는 곳과 BCryptPasswordEncoder로 직접 주입받는 곳 둘 다 만족시킴.
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}