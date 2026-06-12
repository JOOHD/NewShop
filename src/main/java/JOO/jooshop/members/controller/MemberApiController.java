package JOO.jooshop.members.controller;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import JOO.jooshop.members.model.request.ResetPasswordRequest;
import JOO.jooshop.members.model.response.MemberResponse;
import JOO.jooshop.members.service.MemberAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 회원 정보 API 컨트롤러.
 * 요청/응답 매핑만 담당 (Thin Controller).
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Slf4j
public class MemberApiController {

    private final MemberAccountService memberAccountService;

    @GetMapping("/member-info")
    public ResponseEntity<MemberResponse> getCurrentMember(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long memberId = userDetails.getMemberId();
        return ResponseEntity.ok(
                MemberResponse.from(memberAccountService.findMemberById(memberId))
        );
    }

    /**
     * 비밀번호 변경.
     * PUT /api/v1/members/{memberId}/password
     */
    @PutMapping("/{memberId}/password")
    public ResponseEntity<String> changePassword(
            @PathVariable Long memberId,
            @RequestBody ResetPasswordRequest request
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        memberAccountService.changePassword(memberId, request.getNew_password());
        return ResponseEntity.ok("비밀번호가 변경되었습니다.");
    }
}
