package JOO.jooshop.profiile.controller;

import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import JOO.jooshop.profiile.model.MemberProfileDTO;
import JOO.jooshop.profiile.model.ProfileUpdateDTO;
import JOO.jooshop.profiile.service.ProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "프로필", description = "회원 프로필 조회/수정")
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;

    // 프로필 조회 — 닉네임/이메일 + 프로필 정보를 묶어 반환 (본인 또는 관리자만)
    @GetMapping("/{memberId}")
    public ResponseEntity<MemberProfileDTO> getProfile(@PathVariable Long memberId) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return ResponseEntity.ok(profileService.getProfile(memberId));
    }

    // 프로필 수정 — 닉네임, 성별, 연령대 변경 (본인 또는 관리자만)
    @PutMapping("/{memberId}")
    public ResponseEntity<String> updateProfile(
            @PathVariable Long memberId,
            @RequestBody ProfileUpdateDTO dto
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        profileService.updateProfile(memberId, dto);
        return ResponseEntity.ok("프로필이 수정되었습니다.");
    }

    // 프로필 이미지 경로 조회 (캐시 사용)
    @GetMapping("/Images/{memberId}")
    public ResponseEntity<String> getProfileImages(@PathVariable Long memberId) throws Exception {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return ResponseEntity.ok(profileService.getProfileImages(memberId));
    }

    // 프로필 이미지를 URL 문자열로 등록 (파일 업로드는 /{memberId}/image 사용)
    @PostMapping("/Images/{memberId}")
    public ResponseEntity<String> uploadProfileImages(
            @PathVariable Long memberId,
            @RequestParam("imageUrl") String imageUrl
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return profileService.uploadProfileImages(memberId, imageUrl);
    }

    /** 프로필 이미지 파일 업로드 (아바타 클릭 → 파일 선택 → 즉시 업로드) */
    @PostMapping("/{memberId}/image")
    public ResponseEntity<String> uploadProfileImageFile(
            @PathVariable Long memberId,
            @RequestParam("file") MultipartFile file
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return ResponseEntity.ok(profileService.uploadProfileImageFile(memberId, file));
    }

    // 프로필 이미지 삭제 — 기본 이미지로 되돌림
    @DeleteMapping("/Images/{memberId}")
    public ResponseEntity<String> deleteProfileImages(@PathVariable Long memberId) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        profileService.deleteProfileImages(memberId);
        return ResponseEntity.ok("프로필 이미지가 삭제되었습니다.");
    }
}