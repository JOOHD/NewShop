package JOO.jooshop.admin.members.model;

import JOO.jooshop.members.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AdminMemberDetailResponse {

    private Long id;
    private String email;
    private String username;
    private String nickname;
    private String phoneNumber;
    private String memberRole;
    private boolean certifiedByEmail;
    private boolean active;
    private boolean banned;
    private boolean accountExpired;
    private boolean passwordExpired;

    // 회원 엔티티를 관리자 상세 응답으로 변환
    public static AdminMemberDetailResponse from(Member member) {
        return new AdminMemberDetailResponse(
                member.getId(),
                member.getEmail(),
                member.getUsername(),
                member.getNickname(),
                member.getPhoneNumber(),
                member.getMemberRole().name(),
                member.isCertifiedByEmail(),
                member.isActive(),
                member.isBanned(),
                member.isAccountExpired(),
                member.isPasswordExpired()
        );
    }
}