package JOO.jooshop.global.authentication.oauth2.service;

import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.authentication.oauth2.dto.OAuth2TokenResult;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.RefreshToken;
import JOO.jooshop.members.model.request.RefreshRequest;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private static final long REFRESH_TOKEN_EXPIRATION_SECONDS = 1_209_600L; // 14일

    private final JWTUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public OAuth2TokenResult issueToken(Member member, String role) {
        String accessToken = jwtUtil.createAccessToken(
                "access",
                member.getId().toString(),
                role
        );

        String refreshToken = jwtUtil.createRefreshToken(
                "refresh",
                member.getId().toString(),
                role
        );

        saveOrUpdateRefreshToken(member, refreshToken);

        return OAuth2TokenResult.of(accessToken, refreshToken);
    }

    private void saveOrUpdateRefreshToken(Member member, String newRefreshToken) {
        LocalDateTime expirationDateTime = LocalDateTime.now().plusSeconds(REFRESH_TOKEN_EXPIRATION_SECONDS);

        refreshTokenRepository.findByMember(member)
                .ifPresentOrElse(
                        refreshToken -> updateRefreshToken(refreshToken, newRefreshToken, expirationDateTime),
                        () -> saveNewRefreshToken(member, newRefreshToken, expirationDateTime)
                );
    }

    private void updateRefreshToken(
            RefreshToken refreshToken,
            String newRefreshToken,
            LocalDateTime expirationDateTime
    ) {
        RefreshRequest refreshRequest = RefreshRequest.createRefreshDto(newRefreshToken, expirationDateTime);
        refreshToken.updateRefreshToken(refreshRequest);
    }

    private void saveNewRefreshToken(
            Member member,
            String newRefreshToken,
            LocalDateTime expirationDateTime
    ) {
        RefreshToken refreshToken = new RefreshToken(member, newRefreshToken, expirationDateTime);
        refreshTokenRepository.save(refreshToken);
    }
}