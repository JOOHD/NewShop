package JOO.jooshop.global.authentication.jwts.dto;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import lombok.Builder;
import lombok.Getter;

/**
 * 인증 컨텍스트에서 사용할 회원 Snapshot DTO.
 * Member 엔티티를 SecurityContext에 직접 노출하지 않고,
 * 인증/인가에 필요한 최소 정보만 전달한다.
 */
@Getter
@Builder
public class CustomMemberDto {

    private Long memberId;
    private String email;
    private String username;
    private String ordererName;
    private String password;
    private String phoneNumber;
    private MemberRole memberRole;

    private boolean active;
    private boolean banned;
    private boolean passwordExpired;
    private boolean accountExpired;

    /**
     * Member Aggregate → 인증용 Snapshot DTO 변환.
     */
    public static CustomMemberDto from(Member member) {
        return CustomMemberDto.builder()
                .memberId(member.getId())
                .email(member.getEmail())
                .username(member.getUsername())
                .ordererName(member.getUsername())
                .password(member.getPassword())
                .phoneNumber(member.getPhoneNumber())
                .memberRole(member.getMemberRole())
                .active(member.isActive())
                .banned(member.isBanned())
                .passwordExpired(member.isPasswordExpired())
                .accountExpired(member.isAccountExpired())
                .build();
    }

    /**
     * JWT 인증 복원처럼 최소 정보만 필요한 경우 사용.
     */
    public static CustomMemberDto minimal(Long memberId, MemberRole memberRole) {
        return CustomMemberDto.builder()
                .memberId(memberId)
                .memberRole(memberRole)
                .active(true)
                .banned(false)
                .passwordExpired(false)
                .accountExpired(false)
                .build();
    }
}