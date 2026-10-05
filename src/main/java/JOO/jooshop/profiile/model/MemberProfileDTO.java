package JOO.jooshop.profiile.model;

import JOO.jooshop.profiile.entity.Profiles;
import JOO.jooshop.profiile.entity.enums.MemberAges;
import JOO.jooshop.profiile.entity.enums.MemberGender;
import lombok.Data;

@Data
public class MemberProfileDTO {

    private Long profileId;
    private MemberDTO info;
    private String profileImgName;
    private String profileImgPath;
    private String introduction;
    private MemberAges memberAges;
    private MemberGender memberGender;
    private String createdAt;
    private String updatedAt;

    // 프로필 + 회원 정보를 묶은 응답 DTO 생성자
    public MemberProfileDTO(Long profileId, MemberDTO info, String profileImgName, String profileImgPath, String introduction, MemberAges memberAges, MemberGender memberGender, String createdAt, String updatedAt) {
        this.profileId = profileId;
        this.info = info;
        this.profileImgName = profileImgName;
        this.profileImgPath = profileImgPath;
        this.introduction = introduction;
        this.memberAges = memberAges;
        this.memberGender = memberGender;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 프로필 엔티티와 회원 DTO를 합쳐 응답 생성
    public static MemberProfileDTO createMemberProfileDto(Profiles profiles, MemberDTO memberDto) {
        return new MemberProfileDTO(
                                    profiles.getProfileId(),
                                    memberDto,
                                    profiles.getProfileImgName(),
                                    profiles.getProfileImgPath(),
                                    profiles.getIntroduction(),
                                    profiles.getMemberAges(),
                                    profiles.getMemberGender(),
                                    profiles.getCreatedAt().toString(),
                                    profiles.getUpdatedAt().toString());
    }
}
