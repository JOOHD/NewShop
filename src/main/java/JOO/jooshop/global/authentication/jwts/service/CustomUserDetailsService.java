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
 * 로그인 인증 시, Member Aggregate 를 조회하고,
 * Security 전용 UserDetails 로 변환한다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberAccountService memberService;

    // 사용자 로그인 -> CustomUserDetailsService(loadUserByUsername 반환) -> CustomUserDetails -> JWTFilter 
    // 인증된 사용자 -> Member -> CustomMemberDto -> CustomUserDetailsSerivce -> JWTFilter, Authentication 으로 보내짐.
    @Override
    public UserDetails loadUserByUsername(String email) {

        Member member = memberService.findMemberByEmail(email);

        // 이메일로 인증 확인
        if (!member.isCertifiedByEmail()) {
            throw new AuthenticationServiceException("이메일 인증이 필요합니다.");
        }

        // 1. member(entity) ->(from) customMember(dto)
        return new CustomUserDetails(CustomMemberDto.from(member));
    }
}
