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

    public CustomOAuth2User(OAuthUserInfo oAuthUserInfo) {
        this.oAuthUserInfo = oAuthUserInfo;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oAuthUserInfo.toMap();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(oAuthUserInfo.getRole().toString()));
    }

    @Override
    public String getName() {
        return oAuthUserInfo.getEmail();
    }

    public Long getMemberId() {
        return oAuthUserInfo.getMemberId();
    }

    public String getEmail() {
        return oAuthUserInfo.getEmail();
    }

    public String getUsername() {
        return oAuthUserInfo.getUsername();
    }

    public String getSocialId() {
        return oAuthUserInfo.getSocialId();
    }
}