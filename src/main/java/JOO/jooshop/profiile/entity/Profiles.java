package JOO.jooshop.profiile.entity;

import JOO.jooshop.members.entity.Member;
import JOO.jooshop.profiile.entity.enums.MemberAges;
import JOO.jooshop.profiile.entity.enums.MemberGender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_profile")
public class Profiles {

    /*
     * [Entity]
     *
     * 기존
     * - 회원 부가 정보 엔티티로 사용되었지만
     *   Member와의 관계에서 어떤 쪽이 관리 주체인지 구조적으로 모호할 수 있었음
     * - 단순 프로필 정보 보관 객체처럼 보일 수 있었음
     *
     * refactoring 26.04
     * - Profile은 Member에 종속된 하위 엔티티
     * - 독립적으로 동작하기보다 Member Aggregate 내부에서 관리
     * - attachTo(member)를 통해 Member와 연관관계 설정
     * - 실제 연결 책임은 Member.attachProfile()에서 시작되도록 설계
     * - 프로필은 회원의 부가 정보를 표현하지만,
     *   생명주기와 관계 관리는 Member 중심으로 통일
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long profileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    private Member member;

    @Column(name = "profile_img_name")
    private String profileImgName;

    @Column(name = "profile_img_path")
    private String profileImgPath;

    @Lob
    private String introduction;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_ages")
    private MemberAges memberAges;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_gender")
    private MemberGender memberGender;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // 프로필 생성자 — 소개글, 이미지 이름/경로 설정
    private Profiles(String introduction, String profileImgName, String profileImgPath) {
        this.introduction = introduction;
        this.profileImgName = profileImgName;
        this.profileImgPath = profileImgPath;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /* 기본 프로필 생성 */
    // 기본 소개글만 있는 기본 프로필 생성
    public static Profiles createDefaultProfile() {
        return new Profiles("자기 소개를 수정해주세요.", null, null);
    }

    /* Aggregate Root(Member)가 child를 편입할 때 호출 */
    // 회원과 연관관계 설정 — Member.attachProfile()에서 호출
    public void attachTo(Member member) {
        this.member = member;
    }

    // 프로필 이미지 경로 변경 (파일명도 함께 갱신)
    public void changeProfileImages(String profileImgPath) {
        this.profileImgPath = profileImgPath;
        this.profileImgName = extractFileName(profileImgPath);
        touch();
    }

    // 연령대 변경
    public void changeMemberAge(MemberAges newAge) {
        this.memberAges = newAge;
        touch();
    }

    // 성별 변경
    public void changeMemberGender(MemberGender newGender) {
        this.memberGender = newGender;
        touch();
    }

    // 수정 시각을 현재로 갱신
    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    // 경로에서 파일명만 추출 (비어 있으면 null)
    private String extractFileName(String ImagesPath) {
        if (ImagesPath == null || ImagesPath.isBlank()) {
            return null;
        }
        return ImagesPath.substring(ImagesPath.lastIndexOf("/") + 1);
    }
}