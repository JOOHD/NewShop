package JOO.jooshop.global.authentication.oauth2.custom.service;

import JOO.jooshop.global.authentication.oauth2.custom.entity.CustomOAuth2User;
import JOO.jooshop.global.authentication.oauth2.dto.SocialLoginCommand;
import JOO.jooshop.global.authentication.oauth2.responsedto.OAuth2Response;
import JOO.jooshop.global.authentication.oauth2.service.OAuth2MemberService;
import JOO.jooshop.global.authentication.oauth2.support.OAuth2ResponseFactory;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.SocialType;
import JOO.jooshop.members.support.OAuthUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2ResponseFactory oAuth2ResponseFactory;
    private final OAuth2MemberService oAuth2MemberService;

    // 네이버/구글 등 소셜 로그인 사용자 정보를 읽어 회원 조회/가입 후 CustomOAuth2User 반환
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        log.info("[OAuth2] provider={}", registrationId);

        OAuth2User oAuth2User = super.loadUser(userRequest);

        OAuth2Response oAuth2Response = oAuth2ResponseFactory.create(
                registrationId,
                oAuth2User.getAttributes()
        );

        SocialLoginCommand command = createCommand(oAuth2Response);
        Member member = oAuth2MemberService.findOrCreateSocialMember(command);

        return createCustomOAuth2User(member);
    }

    // 소셜 응답을 로그인 처리용 Command로 변환 (socialId = 제공자_고유ID)
    private SocialLoginCommand createCommand(OAuth2Response response) {
        String socialId = response.getProvider() + "_" + response.getProviderId();

        return SocialLoginCommand.of(
                socialId,
                response.getEmail(),
                response.getName(),
                mapToSocialType(response.getProvider())
        );
    }

    // provider 문자열을 SocialType으로 변환
    private SocialType mapToSocialType(String provider) {
        return switch (provider) {
            case "naver" -> SocialType.NAVER;
            case "google" -> SocialType.GOOGLE;
            case "kakao" -> SocialType.KAKAO;
            default -> throw new IllegalArgumentException("지원하지 않는 SocialType 입니다. provider=" + provider);
        };
    }

    // 회원 정보로 CustomOAuth2User 생성
    private CustomOAuth2User createCustomOAuth2User(Member member) {
        OAuthUserInfo userInfo = OAuthUserInfo.createOAuthUserDTO(
                member.getId(),
                member.getEmail(),
                member.getUsername(),
                member.getMemberRole(),
                member.getSocialType(),
                member.getSocialId(),
                true
        );

        return new CustomOAuth2User(userInfo);
    }
}