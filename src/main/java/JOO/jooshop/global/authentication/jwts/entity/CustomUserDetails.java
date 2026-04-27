package JOO.jooshop.global.authentication.jwts.entity;

import JOO.jooshop.global.authentication.jwts.dto.CustomMemberDto;
import JOO.jooshop.members.entity.enums.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 인증 객체
 * - DB 조회 책임 없이 CustomMemberDto의 인증 정보만 SecurityContext에 제공
 */
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final CustomMemberDto memberDto;

    /**
     * 권한 반환
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + memberDto.getMemberRole().name()));
    }

    /**
     * 비밀번호 반환
     */
    @Override
    public String getPassword() {
        return memberDto.getPassword();
    }

    /**
     * 회원 ID 반환
     */
    public Long getMemberId() {
        return memberDto.getMemberId();
    }

    /**
     * 로그인용 아이디 반환 (email)
     */
    @Override
    public String getUsername() {
        return memberDto.getEmail();
    }

    /**
     * 화면용 주문자 이름 반환
     */
    public String getOrdererName() {
        return memberDto.getOrdererName();
    }

    /**
     * 사용자 전화번호 반환
     */
    public String getPhoneNumber() {
        return memberDto.getPhoneNumber();
    }

    /**
     * 회원 권한 반환
     */
    public MemberRole getMemberRole() {
        return memberDto.getMemberRole();
    }

    /** 계정 만료 여부 */
    @Override
    public boolean isAccountNonExpired() {
        return !memberDto.isAccountExpired();
    }

    /** 계정 잠김 여부 */
    @Override
    public boolean isAccountNonLocked() {
        return !memberDto.isBanned();
    }

    /** 비밀번호 만료 여부 */
    @Override
    public boolean isCredentialsNonExpired() {
        return !memberDto.isPasswordExpired();
    }

    /** 계정 활성화 여부 */
    @Override
    public boolean isEnabled() {
        return memberDto.isActive();
    }
}
