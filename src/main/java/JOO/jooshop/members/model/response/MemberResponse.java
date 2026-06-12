package JOO.jooshop.members.model.response;

import JOO.jooshop.members.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MemberResponse {
    private Long id;
    private String ordererName;
    private String phoneNumber;

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getUsername(),    // 화면에 표시할 이름
                member.getPhoneNumber()
        );
    }
}
