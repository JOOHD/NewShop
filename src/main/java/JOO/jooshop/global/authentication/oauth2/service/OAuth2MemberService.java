package JOO.jooshop.global.authentication.oauth2.service;

import JOO.jooshop.global.authentication.oauth2.dto.SocialLoginCommand;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.repository.MemberRepository;
import JOO.jooshop.profiile.entity.Profiles;
import JOO.jooshop.profiile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuth2MemberService {

    private final MemberRepository memberRepository;
    private final ProfileRepository profileRepository;

    // socialId로 기존 회원을 찾고, 없으면 소셜 회원으로 신규 가입
    @Transactional
    public Member findOrCreateSocialMember(SocialLoginCommand command) {
        return memberRepository.findBySocialId(command.getSocialId())
                .map(this::activateAndEnsureProfile)
                .orElseGet(() -> createSocialMember(command));
    }

    // 기존 회원 활성화 + 프로필 없으면 생성
    private Member activateAndEnsureProfile(Member member) {
        member.activate();
        ensureProfile(member);
        return member;
    }

    // 소셜 회원 신규 생성 후 기본 프로필 연결
    private Member createSocialMember(SocialLoginCommand command) {
        Member member = Member.registerSocial(
                command.getEmail(),
                command.getUsername(),
                MemberRole.USER,
                command.getSocialType(),
                command.getSocialId()
        );

        member.activate();

        Member savedMember = memberRepository.save(member);
        createProfile(savedMember);

        return savedMember;
    }

    // 프로필이 없으면 기본 프로필 생성
    private void ensureProfile(Member member) {
        boolean existsProfile = profileRepository.findByMemberId(member.getId()).isPresent();

        if (!existsProfile) {
            createProfile(member);
        }
    }

    // 기본 프로필 생성 후 회원에 연결
    private void createProfile(Member member) {
        Profiles profile = Profiles.createDefaultProfile();
        member.attachProfile(profile);
        profileRepository.save(profile);
    }
}