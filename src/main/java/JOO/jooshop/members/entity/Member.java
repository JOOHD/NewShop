package JOO.jooshop.members.entity;

import JOO.jooshop.global.time.BaseEntity;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.entity.enums.SocialType;
import JOO.jooshop.profiile.entity.Profiles;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_social_id", columnNames = "social_id")
        }
)
public class Member extends BaseEntity {

    /*
     * [Entity]
     *
     * 기존
     * - 회원 정보를 저장하는 중심 엔티티였지만
     *   인증, 계정, 프로필 관련 책임 경계가 구조상 명확하지 않을 수 있었음
     * - 상태 변경보다 데이터 보관 역할 비중이 상대적으로 컸음
     * - Profile과의 관계가 존재하더라도
     *   Aggregate Root로서의 주도권이 코드 설명에서 약했음
     *
     * refactoring 26.04
     * - Member = 회원 도메인의 Aggregate Root
     * - 계정의 핵심 식별 정보와 인증/활성화 상태를 관리
     * - Profile 등 하위 엔티티를 직접 연결/관리하는 루트 엔티티 역할 수행
     * - attachProfile() 같은 편의 메서드로 연관관계 일관성 유지
     * - activate(), changePassword() 등
     *   의미 있는 상태 변경 메서드 중심으로 도메인 행위 구성
     * - Setter 기반 변경보다 도메인 메서드 기반 변경을 우선하도록 설계
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "phone", nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false)
    private MemberRole memberRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_type", nullable = false)
    private SocialType socialType;

    @Column(name = "social_id")
    private String socialId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_banned", nullable = false)
    private boolean banned;

    @Column(name = "is_account_expired", nullable = false)
    private boolean accountExpired;

    @Column(name = "is_password_expired", nullable = false)
    private boolean passwordExpired;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "is_certified_email", nullable = false)
    private boolean certifiedByEmail;

    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private Profiles profile;

    // 회원 생성 생성자 — 외부에서는 registerXxx 정적 팩토리로만 생성
    private Member(
            String email,
            String password,
            String username,
            String nickname,
            String phoneNumber,
            MemberRole memberRole,
            SocialType socialType,
            String socialId,
            boolean certifiedByEmail,
            boolean admin
    ) {
        this.email = requireText(email, "이메일은 필수입니다.");
        this.password = requireNonNull(password, "비밀번호는 필수입니다.");
        this.username = requireText(username, "사용자명은 필수입니다.");
        this.nickname = requireText(nickname, "닉네임은 필수입니다.");
        this.phoneNumber = requireNonNull(phoneNumber, "전화번호는 null일 수 없습니다.");
        this.memberRole = requireNonNull(memberRole, "회원 권한은 필수입니다.");
        this.socialType = requireNonNull(socialType, "소셜 타입은 필수입니다.");
        this.socialId = normalizeSocialId(socialId);
        this.certifiedByEmail = certifiedByEmail;
        this.admin = admin;

        this.active = true;
        this.banned = false;
        this.accountExpired = false;
        this.passwordExpired = false;
        this.joinedAt = LocalDateTime.now();
    }

    // 일반(이메일) 회원 생성 — USER 권한, 암호화된 비밀번호 필수
    public static Member registerGeneral(
            String email,
            String encodedPassword,
            String username,
            String nickname,
            String phoneNumber,
            String socialId
    ) {
        return new Member(
                email,
                requireText(encodedPassword, "인코딩된 비밀번호는 필수입니다."),
                username,
                nickname,
                phoneNumber,
                MemberRole.USER,
                SocialType.GENERAL,
                socialId,
                false,
                false
        );
    }

    // 관리자 회원 생성 — ADMIN 권한, 암호화된 비밀번호 필수
    public static Member registerAdmin(
            String email,
            String encodedPassword,
            String username,
            String nickname,
            String phoneNumber,
            String socialId
    ) {
        return new Member(
                email,
                requireText(encodedPassword, "인코딩된 비밀번호는 필수입니다."),
                username,
                nickname,
                phoneNumber,
                MemberRole.ADMIN,
                SocialType.GENERAL,
                socialId,
                true,
                true
        );
    }

    // 소셜 로그인 회원 생성 — 비밀번호 없이 소셜 제공자/ID로 가입
    public static Member registerSocial(
            String email,
            String username,
            MemberRole role,
            SocialType socialType,
            String socialId
    ) {
        return new Member(
                email,
                "",
                username,
                username,
                "",
                requireNonNull(role, "회원 권한은 필수입니다."),
                socialType,
                socialId,
                true,
                role == MemberRole.ADMIN
        );
    }

    /**
     * 1. attachTo 사용하여 Root 답게 보호
     * ㄴ root 답게 란 -> 상태 변경은 외부가 함부로 직접 건드리지 못함
     * ㄴ 반드시 Member 를 통해서만 일어나야 한다.
     *
     * ex)
     * - 외부 서비스가 member.profile = x 직접 못 함
     * - 외부 서비스가 profile.member = member 직접 만지는 대신
     * - 반드시 member.attachProfile(profile)를 타게 함
     *
     * 2. null 체크 시, 최소한의 aggregate 보호
     * ㄴ이미 프로필이 있으면 교체 가능 여부, 또한 통제 가능
     */
    public void attachProfile(Profiles profile) {
        if (profile == null) {
            throw new IllegalArgumentException("프로필은 null일 수 없습니다.");
        }

        this.profile = profile;
        profile.attachTo(this);
    }

    // 이메일 인증 완료 처리
    public void verifyEmail() {
        if (this.certifiedByEmail) {
            return;
        }
        this.certifiedByEmail = true;
    }

    // 비밀번호 변경 — 변경 시 비밀번호 만료 상태도 해제
    public void changePassword(String encodedPassword) {
        this.password = requireText(encodedPassword, "비밀번호는 비어 있을 수 없습니다.");
        this.passwordExpired = false;
    }

    // 닉네임 변경 (빈 값 불가)
    public void changeNickname(String newNickname) {
        this.nickname = requireText(newNickname, "닉네임은 비어 있을 수 없습니다.");
    }

    // 전화번호 변경 (빈 값 불가)
    public void changePhoneNumber(String newPhoneNumber) {
        this.phoneNumber = requireText(newPhoneNumber, "전화번호는 비어 있을 수 없습니다.");
    }

    // 계정 활성화
    public void activate() {
        if (this.active) {
            return;
        }
        this.active = true;
    }

    // 계정 비활성화
    public void deactivate() {
        if (!this.active) {
            return;
        }
        this.active = false;
    }

    // 계정 정지
    public void ban() {
        if (this.banned) {   // 이미 정지 상태면 아무것도 하지 않음
            return;
        }
        this.banned = true;
    }

    // 계정 정지 해제
    public void unban() {
        if (!this.banned) {
            return;
        }
        this.banned = false;
    }

    // 계정 만료 처리
    public void expireAccount() {
        if (this.accountExpired) {
            return;
        }
        this.accountExpired = true;
    }

    // 계정 만료 해제
    public void restoreAccount() {
        if (!this.accountExpired) {
            return;
        }
        this.accountExpired = false;
    }

    // 비밀번호 만료 처리
    public void expirePassword() {
        if (this.passwordExpired) {
            return;
        }
        this.passwordExpired = true;
    }

    // 비밀번호 만료 해제
    public void restorePassword() {
        if (!this.passwordExpired) {
            return;
        }
        this.passwordExpired = false;
    }

    // 관리자 권한 부여
    public void grantAdminRole() {
        if (this.memberRole == MemberRole.ADMIN && this.admin) {
            return;
        }
        this.memberRole = MemberRole.ADMIN;
        this.admin = true;
    }

    // 소셜 로그인 시 최신 이메일/이름/소셜 정보로 갱신
    public void updateSocialLoginInfo(
            String email,
            String username,
            String socialId,
            SocialType socialType
    ) {
        this.email = requireText(email, "이메일은 비어 있을 수 없습니다.");
        this.username = requireText(username, "사용자명은 비어 있을 수 없습니다.");
        this.socialId = normalizeSocialId(socialId);
        this.socialType = requireNonNull(socialType, "소셜 타입은 필수입니다.");
        this.certifiedByEmail = true;
    }

    // 로그인 가능 여부 — 활성 상태이고 정지/계정 만료가 아닐 때
    public boolean isAvailableForLogin() {
        return this.active && !this.banned && !this.accountExpired;
    }

    // 관리자 화면 표시용 계정 상태 문구
    @Transient
    public String getStatusText() {
        if (banned) return "정지";
        if (!active) return "비활성화";
        if (accountExpired) return "계정 만료";
        if (passwordExpired) return "비밀번호 만료";
        if (admin) return "관리자";
        return "활성";
    }

    // 빈 문자열/공백 검증 후 앞뒤 공백을 제거해 반환
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    // null 검증 (null이면 예외)
    private static <T> T requireNonNull(T value, String message) {
        return Objects.requireNonNull(value, message);
    }

    /**
     * socialId = unique 제약
     * socialId는 ""보다 null이 낫다
     */
    private static String normalizeSocialId(String socialId) {
        if (socialId == null) { 
            return null;
        }

        // "" 반복 저장 시, unique 충돌 = dirty checking 발생 방지
        String normalized = socialId.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}