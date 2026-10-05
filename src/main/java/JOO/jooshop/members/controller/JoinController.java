package JOO.jooshop.members.controller;

import JOO.jooshop.global.mail.service.EmailMemberService;
import JOO.jooshop.members.model.request.JoinMemberRequest;
import JOO.jooshop.members.service.MemberAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "회원가입", description = "회원가입 처리 (뷰 + API 혼합)")
@Controller
@RequiredArgsConstructor
@Slf4j
public class JoinController {

    private final MemberAccountService memberAccountService;
    private final EmailMemberService emailMemberService;

    // 회원가입 화면
    @GetMapping("/join")
    public String joinPage() {
        return "members/join";
    }

    // 회원가입 처리 — 이메일 인증을 마친 경우에만 가입
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
