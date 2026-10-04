package JOO.jooshop.global.authentication.jwts.service;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authentication.jwts.dto.CustomMemberDto;
import JOO.jooshop.global.exception.customException.MemberNotFoundException;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.service.MemberAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    /**
     * [2026-10-04 수정]
     * 존재하지 않는 이메일로 로그인하면 MemberNotFoundException(RuntimeException)이 그대로 던져져서
     * Spring Security가 "internal error"(InternalAuthenticationServiceException)로 처리했다.
     * UserDetailsService 규약대로 UsernameNotFoundException으로 변환해서 던지면
     * DaoAuthenticationProvider가 BadCredentialsException으로 바꿔 FormLoginFailureHandler로 넘긴다.
     * (hideUserNotFoundExceptions 기본값 true → "이메일 없음"과 "비밀번호 틀림"이 외부에 같은 응답으로 보임)
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        try {
            Member member = memberService.findMemberByEmail(email);
            return new CustomUserDetails(CustomMemberDto.from(member));
        } catch (MemberNotFoundException e) {
            throw new UsernameNotFoundException("존재하지 않는 회원입니다.", e);
        }
    }
}
