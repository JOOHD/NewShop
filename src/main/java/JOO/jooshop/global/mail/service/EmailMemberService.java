package JOO.jooshop.global.mail.service;

import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.mail.entity.CertificationEntity;
import JOO.jooshop.global.mail.repository.CertificationRepository;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailMemberService {

    private final JWTUtil jwtUtil;
    private final JavaMailSender mailSender;
    private final MemberRepository memberRepository;
    private final CertificationRepository certificationRepository;

    @Value("${spring.backend.url}")
    private String backendUrl;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 인증 요청 시 기존 토큰 삭제 → 새 토큰 생성 → 저장 → 이메일 발송
     */
    @Transactional
    public void sendEmailVerification(String email) throws Exception {
        // 이전 인증 토큰 모두 삭제 (회원 데이터 삭제 NO!)
        certificationRepository.deleteByEmail(email);

        // 새 JWT 이메일 토큰 생성
        String token = jwtUtil.createEmailToken(email);

        CertificationEntity cert = CertificationEntity.create(email, token);
        certificationRepository.save(cert);

        String link = backendUrl + "/api/email/verify?token=" + token;

        sendEmail(email, link);
    }

    // 인증 링크가 담긴 이메일 발송
    private void sendEmail(String to, String link) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        message.setFrom(new InternetAddress(senderEmail));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject("[JooShop] 이메일 인증을 완료해주세요");
        message.setText("이메일 인증을 위해 아래 링크를 클릭하세요:\n" + link, "utf-8");

        mailSender.send(message);
    }

    /**
     * 인증 토큰 검사 → 인증 완료 처리(토큰 삭제) → 인증 성공 이메일 반환
     */
    @Transactional
    public String verifyEmailAndReturnMember(String token) {
        CertificationEntity cert = certificationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 인증 토큰입니다."));

        if (cert.isExpired()) {
            throw new IllegalArgumentException("인증 토큰이 만료되었습니다.");
        }

        certificationRepository.delete(cert);

        return cert.getEmail();
    }

    /**
     * 상태 변경 책임은 Member가 가짐
     * save 호출 없이 dirty checking 기대 가능
     */
    @Transactional
    public void completeEmailVerification(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입된 회원이 아닙니다."));

        member.verifyEmail();
        log.info("회원 이메일 인증 완료: {}", email);
    }

    /**
     * 인증 토큰 검사 및 인증 완료 처리 (토큰 삭제)
     */
    @Transactional
    public void verifyEmail(String token) {
        CertificationEntity cert = certificationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 인증 토큰입니다."));

        if (cert.isExpired()) {
            throw new IllegalArgumentException("인증 토큰이 만료되었습니다.");
        }

        // 인증 완료 → 토큰 삭제 (인증 완료 플래그는 회원가입 시 체크하므로 여기선 필요 없음)
        certificationRepository.delete(cert);
    }

    /**
     * 이메일 인증 여부 확인
     * 인증 토큰이 있으면 아직 인증 안 한 상태이므로 false
     * 인증 토큰 없으면 인증 완료된 것으로 간주 (인증 후 토큰은 삭제됨)
     */
    public boolean isEmailVerified(String email) {
        // 토큰 있으면 false, 없으면 true
        return certificationRepository.findByEmail(email).isEmpty();
    }
}
