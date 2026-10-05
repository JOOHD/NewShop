package JOO.jooshop.profiile.service;

import JOO.jooshop.global.image.ImageUrlResolver;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final MemberAccountService memberAccountService;
    private final ProfileRepository profileRepository;
    private final ImageUrlResolver imageUrlResolver;

    // WebConfig의 /uploads/** 와 같은 경로 — 여기에 저장하면 바로 서빙된다.
    @Value("${file.upload-dir:uploads/}")
    private String uploadDir;

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    // 프로필 조회 — Profiles + Member 정보를 MemberProfileDTO로 변환해 반환
    public MemberProfileDTO getProfile(Long memberId) {
        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));

        MemberDTO memberDTO = MemberDTO.createMemberDto(profile.getMember());
        return MemberProfileDTO.createMemberProfileDto(profile, memberDTO);
    }

    // 프로필 수정 — 닉네임/성별/연령대 반영 (프로필 행이 없으면 기본 프로필 생성)
    @Transactional
    public void updateProfile(Long memberId, ProfileUpdateDTO dto) {
        fillJoinedAtInNewTransaction();

        Member member = memberAccountService.findMemberById(memberId);

        Profiles profile = profileRepository.findByMemberId(memberId).orElse(null);
        if (profile == null) { // 프로필 row가 없는 회원은 기본 프로필을 만들어 연결
            profile = Profiles.createDefaultProfile();
            member.attachProfile(profile);
            profile = profileRepository.save(profile);
        }

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

    // joinedAt이 null인 기존 회원 데이터를 별도 트랜잭션에서 보정
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fillJoinedAtInNewTransaction() {
        int updated = memberAccountService.fillNullJoinedAt();
        log.info("Updated {} members' joinedAt", updated);
    }

    // 프로필 이미지 경로 조회 — 결과를 캐시에 저장
    @Cacheable(value = "profileImages", key = "#memberId")
    public String getProfileImages(Long memberId) throws Exception {
        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));
        return profile.getProfileImgPath();
    }

    // 프로필 이미지를 URL로 등록 — DB 반영 후 캐시도 새 값으로 갱신
    @Transactional
    @CachePut(value = "profileImages", key = "#memberId")
    public ResponseEntity<String> uploadProfileImages(Long memberId, String imageUrl) {
        String normalizedUrl = imageUrlResolver.normalizeExternalUrl(imageUrl);

        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));

        profile.changeProfileImages(normalizedUrl);
        return ResponseEntity.ok(profile.getProfileImgPath());
    }

    // 프로필 이미지 삭제 — 경로를 null로 만들고 캐시 제거
    @Transactional
    @CacheEvict(value = "profileImages", key = "#memberId")
    public void deleteProfileImages(Long memberId) {
        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found"));

        profile.changeProfileImages(null);
    }

    /**
     * 프로필 이미지 파일 업로드.
     * - content-type 허용 목록으로 이미지 여부 검증 (확장자는 원본 이름이 아닌 content-type으로 결정)
     * - 파일명은 UUID로 새로 만들어 경로 조작/덮어쓰기 방지
     * - 테스트 SQL 등으로 만든 회원처럼 프로필 row가 없으면 기본 프로필을 만들어 연결
     */
    @Transactional
    @CacheEvict(value = "profileImages", key = "#memberId")
    public String uploadProfileImageFile(Long memberId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지를 선택하세요.");
        }
        String ext = ALLOWED_TYPES.get(file.getContentType());
        if (ext == null) {
            throw new IllegalArgumentException("jpg, png, webp, gif 이미지만 업로드할 수 있습니다.");
        }

        Member member = memberAccountService.findMemberById(memberId);
        Profiles profile = profileRepository.findByMemberId(memberId).orElse(null);
        if (profile == null) {
            profile = Profiles.createDefaultProfile();
            member.attachProfile(profile);
            profile = profileRepository.save(profile);
        }

        String filename = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(uploadDir).toAbsolutePath().resolve("profile");
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장에 실패했습니다.", e);
        }

        String publicPath = "/uploads/profile/" + filename;
        profile.changeProfileImages(publicPath);
        return publicPath;
    }
}
