package JOO.jooshop.global.authentication.oauth2.service;

import JOO.jooshop.global.authentication.oauth2.client.KakaoOAuthClient;
import JOO.jooshop.global.authentication.oauth2.dto.KakaoProfileResponse;
import JOO.jooshop.global.authentication.oauth2.dto.OAuth2TokenResult;
import JOO.jooshop.global.authentication.oauth2.dto.OAuthTokenResponse;
import JOO.jooshop.global.authentication.oauth2.dto.SocialLoginCommand;
import JOO.jooshop.global.authentication.oauth2.dto.SocialTokenResponse;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuth2LoginService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OAuth2MemberService oAuth2MemberService;
    private final OAuth2TokenService oAuth2TokenService;

    public String createKakaoAuthorizationUrl() {
        return kakaoOAuthClient.createAuthorizationUrl();
    }

    @Transactional
    public SocialTokenResponse loginWithKakao(String code) {
        OAuthTokenResponse tokenResponse = kakaoOAuthClient.requestAccessToken(code);
        KakaoProfileResponse profileResponse = kakaoOAuthClient.requestProfile(tokenResponse.getAccessToken());

        SocialLoginCommand command = SocialLoginCommand.kakao(profileResponse);
        Member member = oAuth2MemberService.findOrCreateSocialMember(command);

        OAuth2TokenResult tokenResult = oAuth2TokenService.issueToken(
                member,
                MemberRole.USER.toString()
        );

        return SocialTokenResponse.of(
                tokenResult.getAccessToken(),
                tokenResult.getRefreshToken(),
                member.getEmail()
        );
    }
}