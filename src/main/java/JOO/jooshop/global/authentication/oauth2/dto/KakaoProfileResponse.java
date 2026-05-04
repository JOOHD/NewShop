package JOO.jooshop.global.authentication.oauth2.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Properties;

@Data
public class KakaoProfileResponse {
    /*
        카카오 OAuth2 로그인 이후에 응답받은 사용자 정보를 객체 형태로 매핑을 위한 클래스,
        이후 OAuthToken과 SocialResponse로 변환되어 응답으로 제공됩니다.

        @JsonProperty : 카카오에서 제공하는 JSON 필드명과 매핑

        KakaoProfile: 최상위 클래스, 카카오 사용자 프로필을 대표하는 클래스입니다.
        Properties: 사용자의 프로필 속성(닉네임 등)을 담고 있는 클래스입니다.
        KakaoAccount: 카카오 계정과 관련된 정보(이메일, 인증 상태 등)를 담고 있는 클래스입니다.
        Profile: KakaoAccount 내부에 포함된 사용자 프로필 세부 정보(닉네임 등)를 담고 있는 클래스입니다.
     */

    private long id;                    // 카카오 사용자 고유 ID

    @JsonProperty("connected_at")
    private String connectedAt;         // 서비스 연결 시각 (UTC, ISO 8601 형식)

    private Properties properties;      // 프로필 속성 (닉네임 등)

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;  // 카카오 계정 관련 정보 객체

    public String getSocialId() {
        return "kakao_" + id;
    }

    public String getEmail() {
        if (kakaoAccount == null) {
            return null;
        }

        return kakaoAccount.getEmail();
    }

    public String getNickname() {
        if (properties != null && properties.getNickname() != null && !properties.getNickname().isBlank()) {
            return properties.getNickname();
        }

        if (kakaoAccount != null
                && kakaoAccount.getProfile() != null
                && kakaoAccount.getProfile().getNickname() != null
                && !kakaoAccount.getProfile().getNickname().isBlank()) {
            return kakaoAccount.getProfile().getNickname();
        }

        return "kakao_user_" + id;
    }

    // 카카오 계정 정보 클래스
    @Data
    @NoArgsConstructor
    public static class KakaoAccount {
        @JsonProperty("profile_nickname_needs_agreement")
        private boolean profileNicknameNeedsAgreement;  // 닉네임 제공 동의 필요 여부

        private Profile profile;                        // 프로필 정보

        @JsonProperty("has_email")
        private boolean hasEmail;                       // 이메일 보유 여부

        @JsonProperty("email_needs_agreement")
        private boolean emailNeedsAgreement;            // 이메일 제공 동의 필요 여부

        @JsonProperty("is_email_valid")
        private boolean isEmailValid;                   // 이메일 유효 여부

        @JsonProperty("is_email_verified")
        private boolean isEmailVerified;                // 이메일 인증 여부

        private String email;                           // 사용자 이메일

        // 프로필 정보 클래스
        @Getter
        @NoArgsConstructor
        public static class Profile {

            private String nickname;                    // 사용자 닉네임

            @JsonProperty("is_default_nickname")
            private boolean isDefaultNickname;          // 기본 닉네임 여부
        }
    }
}
