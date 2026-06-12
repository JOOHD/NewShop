package JOO.jooshop.profiile.service;

import JOO.jooshop.global.image.ImageUtil;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.profiile.entity.Profiles;
import JOO.jooshop.profiile.entity.enums.MemberAges;
import JOO.jooshop.profiile.entity.enums.MemberGender;
import JOO.jooshop.profiile.model.MemberDTO;
import JOO.jooshop.profiile.model.MemberProfileDTO;
import JOO.jooshop.profiile.model.ProfileUpdateDTO;
import JOO.jooshop.profiile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final MemberAccountService memberAccountService;
    private final ProfileRepository profileRepository;

    public MemberProfileDTO getProfile(Long memberId) {
        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));

        MemberDTO memberDTO = MemberDTO.createMemberDto(profile.getMember());
        return MemberProfileDTO.createMemberProfileDto(profile, memberDTO);
    }

    @Transactional
    public void updateProfile(Long memberId, ProfileUpdateDTO dto) {
        fillJoinedAtInNewTransaction();

        Member member = memberAccountService.findMemberById(memberId);

        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));

        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            member.changeNickname(dto.getNickname());
        }

        if (dto.getAge() != null && !dto.getAge().isBlank()) {
            profile.changeMemberAge(MemberAges.valueOf(dto.getAge()));
        }

        if (dto.getGender() != null && !dto.getGender().isBlank()) {
            profile.changeMemberGender(MemberGender.valueOf(dto.getGender()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fillJoinedAtInNewTransaction() {
        int updated = memberAccountService.fillNullJoinedAt();
        log.info("Updated {} members' joinedAt", updated);
    }

    @Cacheable(value = "profileImages", key = "#memberId")
    public String getProfileImages(Long memberId) throws Exception {
        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));
        return profile.getProfileImgPath();
    }

    @Transactional
    @CachePut(value = "profileImages", key = "#memberId")
    public ResponseEntity<String> uploadProfileImages(Long memberId, MultipartFile ImagesFile) {
        String uploadsDir = "src/main/resources/static/uploads/profileImages/";

        String fileName = UUID.randomUUID().toString().replace("-", "") + ImagesFile.getOriginalFilename();
        String filePath = uploadsDir + fileName;

        try {
            String resizedFileName = ImageUtil.resizeImagesFile(ImagesFile, filePath, "jpeg");
            String resizedDbFilePath = "/uploads/profileImages/" + resizedFileName;

            Profiles profile = profileRepository.findByMemberId(memberId)
                    .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));

            profile.changeProfileImages(resizedDbFilePath);
            return ResponseEntity.ok(profile.getProfileImgPath());

        } catch (IOException e) {
            log.error("Error while processing the Images", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("이미지 처리 중 오류가 발생했습니다.");
        }
    }

    @Transactional
    public void deleteProfileImages(Long memberId) {
        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found"));

        String currentImagesPath = profile.getProfileImgPath();

        profile.changeProfileImages(null);

        if (currentImagesPath != null && !currentImagesPath.isBlank()) {
            String fullPath = "src/main/resources/static" + currentImagesPath;
            deleteImagesFile(fullPath);
        }
    }

    public static void deleteImagesFile(String ImagesPath) {
        try {
            Path path = Paths.get(ImagesPath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.error("파일 삭제 중 오류", e);
        }
    }
}
