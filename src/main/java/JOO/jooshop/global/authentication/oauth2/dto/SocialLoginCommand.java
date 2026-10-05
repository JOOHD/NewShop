package JOO.jooshop.global.authentication.oauth2.dto;

import JOO.jooshop.members.entity.enums.SocialType;
import lombok.Getter;

/**
 * 외부 OAuth2 응답을 바로 Member에 넣지 않기 위한 중간 요청 객체
 */
@Getter
public class SocialLoginCommand {

    private final String socialId;
    private final String email;
    private final String username;
    private final SocialType socialType;

    // 소셜 로그인 요청 생성자 — 필수값 검증, 이메일은 공백이면 null 처리
    private SocialLoginCommand(String socialId, String email, String username, SocialType socialType) {
        validateSocialId(socialId);
        validateUsername(username);
        validateSocialType(socialType);

        this.socialId = socialId;
        this.email = normalize(email);
        this.username = normalize(username);
        this.socialType = socialType;
    }

    // 카카오 프로필로 로그인 Command 생성
    public static SocialLoginCommand kakao(KakaoProfileResponse profile) {
        return new SocialLoginCommand(
                profile.getSocialId(),
                profile.getEmail(),
                profile.getNickname(),
                SocialType.KAKAO
        );
    }

    // 소셜 ID/이메일/이름/타입으로 로그인 Command 생성
    public static SocialLoginCommand of(String socialId, String email, String username, SocialType socialType) {
        return new SocialLoginCommand(socialId, email, username, socialType);
    }

    // 앞뒤 공백 제거, 빈 문자열이면 null
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    // 소셜 ID 필수 검증
    private static void validateSocialId(String socialId) {
        if (socialId == null || socialId.isBlank()) {
            throw new IllegalArgumentException("소셜 ID는 필수입니다.");
        }
    }

    // 소셜 사용자 이름 필수 검증
    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("소셜 사용자 이름은 필수입니다.");
        }
    }

    // 소셜 타입 필수 검증
    private static void validateSocialType(SocialType socialType) {
        if (socialType == null) {
            throw new IllegalArgumentException("소셜 타입은 필수입니다.");
        }
    }
}