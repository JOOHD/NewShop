package JOO.jooshop.global.demo;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.profiile.entity.Profiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 로컬 개발용 테스트 계정 자동 생성.
 *
 * 로컬은 ddl-auto: create-drop이라 재기동할 때마다 DB가 비워져 매번 계정을 SQL로 다시 넣어야 했다.
 * ddl-auto가 create-drop일 때만(= 로컬 기본값) 기동 시 테스트 계정을 만든다.
 * 운영(EC2)은 DDL_AUTO=update로 실행하므로 이 클래스가 아예 빈으로 등록되지 않는다.
 *
 * 계정(비밀번호는 모두 Test1234!): admin@jooshop.com (관리자), user@jooshop.com (일반 회원)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(0) // 상품 시딩(DemoProductInitializer, Order 1)보다 먼저 실행
@ConditionalOnProperty(name = "spring.jpa.hibernate.ddl-auto", havingValue = "create-drop")
public class DemoAccountInitializer implements CommandLineRunner {

    private static final String TEST_PASSWORD = "Test1234!";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 앱 시작 시 테스트 계정이 없으면 생성 (이미 있으면 건너뜀)
    @Override
    @Transactional
    public void run(String... args) {
        createIfAbsent("admin@jooshop.com", "admin", "관리자", true);
        createIfAbsent("user@jooshop.com", "user", "일반회원", false);
    }

    // 이메일이 없을 때만 회원 + 기본 프로필 생성 (이메일 중복은 로그인 오류의 원인이라 반드시 확인)
    private void createIfAbsent(String email, String username, String nickname, boolean admin) {
        if (memberRepository.existsByEmail(email)) {
            return;
        }

        String encoded = passwordEncoder.encode(TEST_PASSWORD);
        String socialId = (admin ? "admin-" : "general-") + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Member member = admin
                ? Member.registerAdmin(email, encoded, username, nickname, "01012345678", socialId)
                : Member.registerGeneral(email, encoded, username, nickname, "01012345678", socialId);

        member.verifyEmail(); // 이메일 인증 완료 상태로 생성
        member.attachProfile(Profiles.createDefaultProfile());
        memberRepository.save(member);

        log.info("[DemoAccount] created test account: {}", email);
    }
}
