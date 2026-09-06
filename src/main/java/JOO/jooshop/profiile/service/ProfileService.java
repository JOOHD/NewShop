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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final MemberAccountService memberAccountService;
    private final ProfileRepository profileRepository;
    private final ImageUrlResolver imageUrlResolver;

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
    public ResponseEntity<String> uploadProfileImages(Long memberId, String imageUrl) {
        String normalizedUrl = imageUrlResolver.normalizeExternalUrl(imageUrl);

        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + memberId));

        profile.changeProfileImages(normalizedUrl);
        return ResponseEntity.ok(profile.getProfileImgPath());
    }

    @Transactional
    @CacheEvict(value = "profileImages", key = "#memberId")
    public void deleteProfileImages(Long memberId) {
        Profiles profile = profileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found"));

        profile.changeProfileImages(null);
    }
}
