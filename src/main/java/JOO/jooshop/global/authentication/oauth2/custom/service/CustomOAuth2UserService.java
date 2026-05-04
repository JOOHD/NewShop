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

    private SocialLoginCommand createCommand(OAuth2Response response) {
        String socialId = response.getProvider() + "_" + response.getProviderId();

        return SocialLoginCommand.of(
                socialId,
                response.getEmail(),
                response.getName(),
                mapToSocialType(response.getProvider())
        );
    }

    private SocialType mapToSocialType(String provider) {
        return switch (provider) {
            case "naver" -> SocialType.NAVER;
            case "google" -> SocialType.GOOGLE;
            case "kakao" -> SocialType.KAKAO;
            default -> throw new IllegalArgumentException("지원하지 않는 SocialType 입니다. provider=" + provider);
        };
    }

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