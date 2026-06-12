package JOO.jooshop.global.mail.controller;

import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.TokenCookieWriter;
import JOO.jooshop.global.mail.repository.CertificationRepository;
import JOO.jooshop.global.mail.service.EmailMemberService;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/email")
public class EmailVerificationController {

    /**
     * CertificationEntity: 토큰 저장 및 만료 관리
     * EmailMemberService: 토큰 생성, 인증 처리, 메일 발송 담당
     * Member: 인증 여부만 관리 (certifiedByEmail)
     * 컨트롤러는 /verify, /resend 기능만 담당
     *
     * refactoring 26.06
     * - JWTUtil + 직접 Cookie 생성 제거
     * - MemberRepository 직접 의존 → MemberAccountService 경유
     * - TokenService.issueLoginTokens() + TokenCookieWriter.write() 사용
     */
    private final EmailMemberService emailMemberService;
    private final MemberAccountService memberAccountService;
    private final CertificationRepository certificationRepository;
    private final TokenService tokenService;
    private final TokenCookieWriter tokenCookieWriter;

    // 인증 메일 발송 요청 (POST)
    @PostMapping("/verify-request")
    public ResponseEntity<?> sendVerificationEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body("이메일이 필요합니다.");
        }
        try {
            emailMemberService.sendEmailVerification(email);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("메일 발송 실패");
        }
    }

    // 이메일 인증 링크 클릭 시 (GET)
    @GetMapping("/verify")
    public String verifyEmail(@RequestParam("token") String token,
                              HttpServletResponse response,
                              Model model) {
        try {
            String email = emailMemberService.verifyEmailAndReturnMember(token);
            model.addAttribute("verifiedEmail", email);

            // 이미 가입된 회원이면 accessToken 발급하여 자동 로그인 처리
            Optional<Member> memberOpt = memberAccountService.findMemberByEmailOptional(email);
            memberOpt.ifPresent(member -> {
                var tokenResponse = tokenService.issueLoginTokens(member, member.getMemberRole().name());
                tokenCookieWriter.write(response, tokenResponse.getAccessToken(), tokenResponse.getRefreshToken());
            });

            return "email/verifySuccess";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "email/verifyFail";
        }
    }

    // 이메일 인증 상태 확인
    @GetMapping("/verify-check")
    public ResponseEntity<Map<String, Boolean>> verifyCheck(@RequestParam("email") String email) {
        boolean isVerified = certificationRepository.findByEmail(email).isEmpty();

        // 항상 200 OK 응답이고, body가 {"verified":true} 또는 {"verified":false} 형태
        return ResponseEntity.ok(Map.of("verified", isVerified));
    }
}
