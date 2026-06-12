package JOO.jooshop.profiile.controller;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import JOO.jooshop.profiile.entity.Profiles;
import JOO.jooshop.profiile.model.MemberDTO;
import JOO.jooshop.profiile.model.MemberProfileDTO;
import JOO.jooshop.profiile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ProfileViewController {

    private final MemberAccountService memberAccountService;
    private final ProfileRepository profileRepository;

    @GetMapping("/profile")
    public String profilePage(Principal principal, Model model) {
        if (principal == null) return "redirect:/login";

        Member member = memberAccountService.findMemberByEmail(principal.getName());

        Optional<Profiles> profilesOpt = profileRepository.findByMemberId(member.getId());

        MemberDTO memberDTO = MemberDTO.createMemberDto(member);

        MemberProfileDTO memberProfileDTO = profilesOpt
                .map(profiles -> MemberProfileDTO.createMemberProfileDto(profiles, memberDTO))
                .orElseGet(() -> new MemberProfileDTO(null, memberDTO, null, null, "", null, null, "", ""));

        model.addAttribute("member", memberProfileDTO);

        return "members/profile";
    }
}
