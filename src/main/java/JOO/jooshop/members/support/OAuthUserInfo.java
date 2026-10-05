package JOO.jooshop.members.support;


import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.entity.enums.SocialType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class OAuthUserInfo {

    private Long memberId;
    private String username;
    private String email;
    private MemberRole role;
    private SocialType socialType;
    private String socialId;
    private boolean isCertify;

    // 소셜 로그인 사용자 정보 객체 생성
    public static OAuthUserInfo createOAuthUserDTO(Long memberId, String email, String username, MemberRole role, SocialType socialType, String socialId, boolean isCertifty) {
        OAuthUserInfo userDTO = new OAuthUserInfo();
        userDTO.setMemberId(memberId); // Member Id 를 반환하도록 추가
        userDTO.setEmail(email);
        userDTO.setUsername(username);
        userDTO.setRole(role);
        userDTO.setSocialType(socialType);
        userDTO.setSocialId(socialId);
        userDTO.setCertify(isCertifty);
        return userDTO;
    }

    // OAuth2User 속성 맵 형태로 변환
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("memberId", memberId); // Member Id 를 반환하도록 추가
        map.put("email", this.email);
        map.put("username", this.username);
        map.put("role", this.role.toString());
        map.put("socialType", this.socialType.toString());
        map.put("socialId", this.socialId);
        map.put("is_certify_by_email", this.isCertify);
        return map;
    }
}
