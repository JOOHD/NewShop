package JOO.jooshop.members.service;

import JOO.jooshop.global.exception.customException.ExistingMemberException;
import JOO.jooshop.global.exception.customException.InvalidCredentialsException;
import JOO.jooshop.global.exception.customException.MemberNotFoundException;
import JOO.jooshop.global.mail.service.EmailMemberService;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.model.request.JoinMemberRequest;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.profiile.entity.Profiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAccountService {

    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailMemberService emailMemberService;
    private final MemberRepository memberRepository;

    /**
     * 회원가입 / 회원조회 / 회원상태변경 / 비밀번호 변경
     * = Member 엔티티 상태를 관리하는 서비스
     */

    @Transactional
    public Member registerMember(JoinMemberRequest request) {
        validateDuplicateEmail(request.getEmail());
        validatePasswordMatch(request.getPassword1(), request.getPassword2());

        String socialId = generateSocialId();

        Member member = Member.registerGeneral(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword1()),
                request.getUsername(),
                request.getNickname(),
                request.getPhoneNumber(),
                socialId
        );

        Profiles profile = Profiles.createDefaultProfile();
        member.attachProfile(profile);

        Member savedMember = memberRepository.save(member);
        sendVerificationEmail(savedMember.getEmail());

        return savedMember;
    }

    public void validateDuplicateEmail(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new ExistingMemberException(email);
        }
    }

    public Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberNotFoundException(email));
    }

    /**
     * 이메일 인증 완료 후 회원 존재 여부를 확인할 때처럼,
     * 회원이 없어도 예외가 아닌 Optional을 반환한다.
     */
    public Optional<Member> findMemberByEmailOptional(String email) {
        return memberRepository.findByEmail(email);
    }

    public Member findMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new MemberNotFoundException("해당 ID로 사용자를 찾을 수 없습니다: " + id));
    }

    /**
     * socialId로 회원을 조회한다.
     * OAuth2 로그인 성공 후, Member를 찾을 때 사용한다.
     */
    public Member findMemberBySocialId(String socialId) {
        return memberRepository.findBySocialId(socialId)
                .orElseThrow(() -> new MemberNotFoundException(
                        "해당 소셜 ID로 사용자를 찾을 수 없습니다.: " + socialId
                )); 
    }

    @Transactional
    public void resetPassword(Long memberId, String currentPassword, String newPassword, String newPasswordConfirm) {
        Member member = findMemberById(memberId);

        validateCurrentPassword(currentPassword, member.getPassword());
        validatePasswordMatch(newPassword, newPasswordConfirm);

        member.changePassword(passwordEncoder.encode(newPassword));
        log.info("회원 {} 비밀번호가 성공적으로 변경되었습니다.", member.getEmail());
    }

    @Transactional
    public void verifyEmail(Long memberId) {
        Member member = findMemberById(memberId);
        member.verifyEmail();
    }

    @Transactional
    public void activate(Long id) {
        Member member = findMemberById(id);
        if (member.isActive()) {
            throw new IllegalStateException("이미 활성화된 계정입니다.");
        }
        member.activate();
    }

    @Transactional
    public void deactivate(Long id) {
        Member member = findMemberById(id);
        if (!member.isActive()) {
            throw new IllegalStateException("이미 비활성화된 계정입니다.");
        }
        member.deactivate();
    }

    @Transactional
    public void ban(Long id) {
        Member member = findMemberById(id);
        if (member.isBanned()) {
            throw new IllegalStateException("이미 정지된 계정입니다.");
        }
        member.ban();
    }

    @Transactional
    public void unban(Long id) {
        Member member = findMemberById(id);
        if (!member.isBanned()) {
            throw new IllegalStateException("이미 정지 해제된 계정입니다.");
        }
        member.unban();
    }

    @Transactional
    public void expireAccount(Long id) {
        Member member = findMemberById(id);
        if (member.isAccountExpired()) {
            throw new IllegalStateException("이미 만료된 계정입니다.");
        }
        member.expireAccount();
    }

    @Transactional
    public void renewAccount(Long id) {
        Member member = findMemberById(id);
        if (!member.isAccountExpired()) {
            throw new IllegalStateException("이미 만료 해제된 계정입니다.");
        }
        member.restoreAccount();
    }

    @Transactional
    public void expirePassword(Long id) {
        Member member = findMemberById(id);
        if (member.isPasswordExpired()) {
            throw new IllegalStateException("이미 만료된 비밀번호입니다.");
        }
        member.expirePassword();
    }

    @Transactional
    public void renewPassword(Long id) {
        Member member = findMemberById(id);
        if (!member.isPasswordExpired()) {
            throw new IllegalStateException("이미 만료 해제된 비밀번호입니다.");
        }
        member.restorePassword();
    }

    @Transactional
    public void changePassword(Long id, String newPassword) {
        Member member = findMemberById(id);
        member.changePassword(passwordEncoder.encode(newPassword));
    }

    /**
     * joinedAt이 null인 회원들의 joinedAt을 일괄 업데이트한다.
     * ProfileService에서 REQUIRES_NEW 트랜잭션으로 호출된다.
     */
    @Transactional
    public int fillNullJoinedAt() {
        return memberRepository.fillNullJoinedAt();
    }

    private void validatePasswordMatch(String password1, String password2) {
        if (!password1.equals(password2)) {
            throw new InvalidCredentialsException("비밀번호가 서로 일치하지 않습니다.");
        }
    }

    private void validateCurrentPassword(String rawPassword, String encodedPassword) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            throw new InvalidCredentialsException("기존 비밀번호가 일치하지 않습니다.");
        }
    }

    private void sendVerificationEmail(String email) {
        try {
            emailMemberService.sendEmailVerification(email);
        } catch (Exception e) {
            log.error("이메일 전송 실패", e);
        }
    }

    private String generateSocialId() {
        return "general-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}