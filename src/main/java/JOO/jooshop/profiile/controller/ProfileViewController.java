package JOO.jooshop.profiile.controller;

import JOO.jooshop.global.authentication.support.AuthenticatedMemberResolver;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.profiile.entity.Profiles;
import JOO.jooshop.profiile.model.MemberDTO;
import JOO.jooshop.profiile.model.MemberProfileDTO;
import JOO.jooshop.profiile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ProfileViewController {

    private final MemberAccountService memberAccountService;
    private final ProfileRepository profileRepository;

    // Principal.getName()(이메일 기준 조회) 대신 memberId로 조회하도록 변경.
    // 소셜 로그인(카카오 등)은 principal 타입이 CustomUserDetails가 아니라 CustomOAuth2User라
    // 이메일 동의 여부와 무관하게 항상 memberId 기반으로 안전하게 조회하기 위함.
    @GetMapping("/profile")
    public String profilePage(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) return "redirect:/login";

        Long memberId = AuthenticatedMemberResolver.resolveMemberId(authentication);
        Member member = memberAccountService.findMemberById(memberId);

        Optional<Profiles> profilesOpt = profileRepository.findByMemberId(member.getId());

        MemberDTO memberDTO = MemberDTO.createMemberDto(member);

        MemberProfileDTO memberProfileDTO = profilesOpt
                .map(profiles -> MemberProfileDTO.createMemberProfileDto(profiles, memberDTO))
                .orElseGet(() -> new MemberProfileDTO(null, memberDTO, null, null, "", null, null, "", ""));

        model.addAttribute("member", memberProfileDTO);

        return "members/profile";
    }
}
