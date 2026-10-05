package JOO.jooshop.profiile.model;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.entity.enums.SocialType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class MemberDTO {

    private Long id;
    private String email;
    private String username;
    private String nickname;
    private String phoneNumber;
    private MemberRole member_role;
    private SocialType social_type;
    private Boolean is_active;
    private Boolean is_admin;
    private Boolean is_certified_email;
    private String joined_at;

    public MemberDTO
            (Long id,
             String email,
             String username,
             String nickname,
             String phoneNumber,
             MemberRole member_role,
             SocialType social_type,
             Boolean is_active,
             Boolean is_admin,
             Boolean is_certified_email,
             String joined_at)
    {
        this.id = id;
        this.email = email;
        this.username = username;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
        this.member_role = member_role;
        this.social_type = social_type;
        this.is_active = is_active;
        this.is_admin = is_admin;
        this.is_certified_email = is_certified_email;
        this.joined_at = joined_at;
    }

    /* MemberController getCurrentMember method */
    public MemberDTO
            (Long id,
            String username,
            String phoneNumber)
    {
        this.id = id;
        this.username = username;
        this.phoneNumber = phoneNumber;
    }

    // 회원 엔티티에서 화면용 정보(id, 닉네임, 이메일 등)만 추출
    public static MemberDTO createMemberDto(Member member) {
        return new MemberDTO(member.getId(),
                             member.getEmail(),
                             member.getUsername(),
                             member.getNickname(),
                             member.getPhoneNumber(),
                             member.getMemberRole(),
                             member.getSocialType(),
                             member.isActive(),
                             member.isAdmin(),
                             member.isCertifiedByEmail(),
                             // joinedAt null 체크 후 변환
                             member.getJoinedAt() != null ? member.getJoinedAt().toString() : ""
        );
    }
}
