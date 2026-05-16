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


/**
 * OAuth2 관련 클래스 중 가장 핵심 클래스
 * OAuth2 provider에서 받은 사용자 정보를 내부 Member로 변환하고
 * 자체 JWT 발급 흐름으로 연결한다.

 * 핵심 역할
 * code → provider access token 요청
 * provider access token → provider user info 조회
 * provider user info → Member 조회 or 생성
 * Member → TokenService.issueToken(member)
 */
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