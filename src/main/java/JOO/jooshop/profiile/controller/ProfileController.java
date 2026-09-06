package JOO.jooshop.profiile.controller;

import JOO.jooshop.global.authorization.MemberAuthorizationUtil;
import JOO.jooshop.profiile.model.MemberProfileDTO;
import JOO.jooshop.profiile.model.ProfileUpdateDTO;
import JOO.jooshop.profiile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/{memberId}")
    public ResponseEntity<MemberProfileDTO> getProfile(@PathVariable Long memberId) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return ResponseEntity.ok(profileService.getProfile(memberId));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<String> updateProfile(
            @PathVariable Long memberId,
            @RequestBody ProfileUpdateDTO dto
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        profileService.updateProfile(memberId, dto);
        return ResponseEntity.ok("프로필이 수정되었습니다.");
    }

    @GetMapping("/Images/{memberId}")
    public ResponseEntity<String> getProfileImages(@PathVariable Long memberId) throws Exception {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return ResponseEntity.ok(profileService.getProfileImages(memberId));
    }

    @PostMapping("/Images/{memberId}")
    public ResponseEntity<String> uploadProfileImages(
            @PathVariable Long memberId,
            @RequestParam("imageUrl") String imageUrl
    ) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        return profileService.uploadProfileImages(memberId, imageUrl);
    }

    @DeleteMapping("/Images/{memberId}")
    public ResponseEntity<String> deleteProfileImages(@PathVariable Long memberId) {
        MemberAuthorizationUtil.verifyUserIdMatch(memberId);
        profileService.deleteProfileImages(memberId);
        return ResponseEntity.ok("프로필 이미지가 삭제되었습니다.");
    }
}