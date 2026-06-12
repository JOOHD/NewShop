package JOO.jooshop.members.controller;

import JOO.jooshop.global.mail.service.EmailMemberService;
import JOO.jooshop.members.model.request.JoinMemberRequest;
import JOO.jooshop.members.service.MemberAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * 회원가입 뷰 + API 컨트롤러.
 * 예외 처리는 GlobalExceptionHandler에 위임 — try-catch 불필요.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class JoinController {

    private final MemberAccountService memberAccountService;
    private final EmailMemberService emailMemberService;

    @GetMapping("/join")
    public String joinPage() {
        return "members/join";
    }

    @PostMapping("/api/join")
    @ResponseBody
    public ResponseEntity<String> join(@RequestBody @Valid JoinMemberRequest request) {
        if (!emailMemberService.isEmailVerified(request.getEmail())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("이메일 인증이 필요합니다.");
        }

        memberAccountService.registerMember(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("회원가입 성공");
    }
}
