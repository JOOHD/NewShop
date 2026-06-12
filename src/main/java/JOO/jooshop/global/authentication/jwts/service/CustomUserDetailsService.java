package JOO.jooshop.global.authentication.jwts.service;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authentication.jwts.dto.CustomMemberDto;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;


/**
 * email로 Member를 조회해 Spring Security 인증용 UserDetails를 반환한다.
 * 인증 상태 검증(이메일 인증 여부 등)은 FormLoginSuccessHandler에서 담당한다.

 * 핵심 역할
 * UserDetailsService: 사용자를 찾는 역할, 순수 조회
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberAccountService memberService;

    @Override
    public UserDetails loadUserByUsername(String email) {
        Member member = memberService.findMemberByEmail(email);
        return new CustomUserDetails(CustomMemberDto.from(member));
    }
}
