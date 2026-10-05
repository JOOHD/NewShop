package JOO.jooshop.admin.members.controller;

import JOO.jooshop.admin.members.model.AdminMemberDetailResponse;
import JOO.jooshop.admin.members.model.AdminMemberResponse;
import JOO.jooshop.admin.members.service.AdminMemberService;
import JOO.jooshop.global.exception.customException.UnverifiedEmailException;
import JOO.jooshop.global.mail.service.EmailMemberService;
import JOO.jooshop.members.model.request.JoinMemberRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자용 회원 API 컨트롤러
 * - 전체 조회, 상세 조회, 관리자 계정 생성
 * - 계정 상태 변경 (활성화, 비활성화, 정지 등)
 */
@Tag(name = "관리자 - 회원", description = "관리자 전용 회원 조회/상태 변경")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/members")
@PreAuthorize("hasRole('ADMIN')") // ADMIN 권한 필요
public class AdminMemberApiController {

    private final AdminMemberService adminMemberService;

    /**
     * 전체 회원 조회
     * - 목록용 DTO(AdminMemberResponse)로 반환
     */
    @GetMapping
    public ResponseEntity<List<AdminMemberResponse>> getMemberList() {
        List<AdminMemberResponse> list = adminMemberService.findAllMembers()
                .stream()
                .map(AdminMemberResponse::from) // Entity → DTO 변환
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /**
     * 단일 회원 상세 조회
     * - 상세용 DTO(AdminMemberDetailResponse)로 반환
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminMemberDetailResponse> getMemberDetail(@PathVariable Long id) {
        return ResponseEntity.ok(
                AdminMemberDetailResponse.from(adminMemberService.findMemberById(id))
        );
    }

    // 관리자 계정 등록
    @PostMapping("/join")
    @ResponseBody
    public ResponseEntity<?> registerAdmin(@RequestBody @Valid JoinMemberRequest request) {
        adminMemberService.registerAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("관리자 등록 성공");
    }

    /** 회원 활성화 */
    @PostMapping("/activate/{id}")
    public ResponseEntity<String> activate(@PathVariable Long id) {
        adminMemberService.activate(id);
        return ResponseEntity.ok("회원 활성화되었습니다.");
    }

    /** 회원 비활성화 */
    @PostMapping("/deactivate/{id}")
    public ResponseEntity<String> deactivate(@PathVariable Long id) {
        adminMemberService.deactivate(id);
        return ResponseEntity.ok("회원 비활성화되었습니다.");
    }

    /** 회원 정지 */
    @PostMapping("/ban/{id}")
    public ResponseEntity<String> ban(@PathVariable Long id) {
        adminMemberService.ban(id);
        return ResponseEntity.ok("회원 정지되었습니다.");
    }

    /** 회원 정지 해제 */
    @PostMapping("/unban/{id}")
    public ResponseEntity<String> unban(@PathVariable Long id) {
        adminMemberService.unban(id);
        return ResponseEntity.ok("회원 정지가 해제되었습니다.");
    }

    /** 계정 만료 처리 */
    @PostMapping("/expire-a ccount/{id}")
    public ResponseEntity<String> expireAccount(@PathVariable Long id) {
        adminMemberService.expireAccount(id);
        return ResponseEntity.ok("회원 계정이 만료 처리되었습니다.");
    }

    /** 계정 만료 해제 */
    @PostMapping("/renew-account/{id}")
    public ResponseEntity<String> renewAccount(@PathVariable Long id) {
        adminMemberService.renewAccount(id);
        return ResponseEntity.ok("회원 계정 만료가 해제되었습니다.");
    }

    /** 비밀번호 만료 처리 */
    @PostMapping("/expire-password/{id}")
    public ResponseEntity<String> expirePassword(@PathVariable Long id) {
        adminMemberService.expirePassword(id);
        return ResponseEntity.ok("회원 비밀번호가 만료 처리되었습니다.");
    }

    /** 비밀번호 만료 해제 */
    @PostMapping("/renew-password/{id}")
    public ResponseEntity<String> renewPassword(@PathVariable Long id) {
        adminMemberService.renewPassword(id);
        return ResponseEntity.ok("회원 비밀번호 만료가 해제되었습니다.");
    }
}
