package JOO.jooshop.global.authentication.oauth2.service;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.oauth2.client.KakaoOAuthClient;
import JOO.jooshop.global.authentication.oauth2.dto.KakaoProfileResponse;
import JOO.jooshop.global.authentication.oauth2.dto.OAuthTokenResponse;
import JOO.jooshop.global.authentication.oauth2.dto.SocialLoginCommand;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OAuth2 provider에서 받은 사용자 정보를 내부 Member로 변환하고
 * 자체 JWT 발급 흐름으로 연결한다.
 *
 * 흐름:
 * code → provider access token → provider user info → Member 조회/생성 → JWT 발급
 */
@Service
@RequiredArgsConstructor
public class OAuth2LoginService {

    private final KakaoOAuthClient kakaoOAuthClient;
    private final OAuth2MemberService oAuth2MemberService;
    private final TokenService tokenService;

    public String createKakaoAuthorizationUrl() {
        return kakaoOAuthClient.createAuthorizationUrl();
    }

    @Transactional
    public KakaoLoginResult loginWithKakao(String code) {
        OAuthTokenResponse tokenResponse = kakaoOAuthClient.requestAccessToken(code);
        KakaoProfileResponse profileResponse = kakaoOAuthClient.requestProfile(tokenResponse.getAccessToken());

        SocialLoginCommand command = SocialLoginCommand.kakao(profileResponse);
        Member member = oAuth2MemberService.findOrCreateSocialMember(command);

        TokenResponse token = tokenService.issueLoginTokens(member, MemberRole.USER.name());

        return new KakaoLoginResult(token, member.getEmail());
    }

    /**
     * 카카오 로그인 처리 결과.
     * 컨트롤러가 토큰(쿠키)과 email(응답 바디)을 분리해서 처리할 수 있도록 한다.
     */
    public record KakaoLoginResult(TokenResponse tokenResponse, String email) {}
}