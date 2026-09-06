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

    @Transactional
    public Member findOrCreateSocialMember(SocialLoginCommand command) {
        return memberRepository.findBySocialId(command.getSocialId())
                .map(this::activateAndEnsureProfile)
                .orElseGet(() -> createSocialMember(command));
    }

    private Member activateAndEnsureProfile(Member member) {
        member.activate();
        ensureProfile(member);
        return member;
    }

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

    private void ensureProfile(Member member) {
        boolean existsProfile = profileRepository.findByMemberId(member.getId()).isPresent();

        if (!existsProfile) {
            createProfile(member);
        }
    }

    private void createProfile(Member member) {
        Profiles profile = Profiles.createDefaultProfile();
        member.attachProfile(profile);
        profileRepository.save(profile);
    }
}