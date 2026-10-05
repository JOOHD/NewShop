package JOO.jooshop.global.authentication.oauth2.custom.entity;

import JOO.jooshop.members.support.OAuthUserInfo;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User, Serializable {

    private final OAuthUserInfo oAuthUserInfo;

    // 소셜 로그인 사용자 정보를 감싸는 생성자
    public CustomOAuth2User(OAuthUserInfo oAuthUserInfo) {
        this.oAuthUserInfo = oAuthUserInfo;
    }

    // 소셜 사용자 속성 맵 반환
    @Override
    public Map<String, Object> getAttributes() {
        return oAuthUserInfo.toMap();
    }

    // 회원 권한을 ROLE_ 접두사를 붙인 Security 권한으로 변환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + oAuthUserInfo.getRole().toString()));
    }

    // Security에서 사용하는 사용자 식별 이름 — 이메일
    @Override
    public String getName() {
        return oAuthUserInfo.getEmail();
    }

    // 우리 서비스의 회원 ID
    public Long getMemberId() {
        return oAuthUserInfo.getMemberId();
    }

    // 소셜 계정 이메일
    public String getEmail() {
        return oAuthUserInfo.getEmail();
    }

    // 소셜 계정 이름
    public String getUsername() {
        return oAuthUserInfo.getUsername();
    }

    // 소셜 제공자가 발급한 사용자 고유 ID
    public String getSocialId() {
        return oAuthUserInfo.getSocialId();
    }
}