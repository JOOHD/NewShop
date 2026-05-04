package JOO.jooshop.global.authentication.jwts.service;

import JOO.jooshop.global.authentication.jwts.dto.TokenResponse;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.RefreshToken;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.model.request.RefreshRequest;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import JOO.jooshop.members.service.MemberAccountService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * JWT 발급, 재발급, RefreshToken 저장/갱신을 담당하는 인증 도메인 서비스.
 * Controller/Filter에서 토큰 저장 로직을 제거하고 책임을 집중시킨다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TokenService {

    private static final long REFRESH_TOKEN_EXPIRATION_SECONDS = 60L * 60 * 24 * 14;

    private static final String ACCESS_TOKEN_CATEGORY = "access";
    private static final String REFRESH_TOKEN_CATEGORY = "refresh";

    private final JWTUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberAccountService memberAccountService;

    /**
     * 로그인 성공 후 Access/Refresh Token 발급 및 RefreshToken 저장.
     */
    public TokenResponse issueLoginTokens(Member member, String role) {
        String memberId = String.valueOf(member.getId());

        String accessToken = jwtUtil.createAccessToken(
                ACCESS_TOKEN_CATEGORY,
                memberId,
                role
        );

        String refreshToken = jwtUtil.createRefreshToken(
                REFRESH_TOKEN_CATEGORY,
                memberId,
                role
        );

        saveOrUpdateRefreshToken(member, refreshToken);

        return TokenResponse.of(accessToken, refreshToken);
    }

    /**
     * RefreshToken 검증 후 Access/Refresh Token 재발급.
     */
    public TokenResponse reissue(String refreshToken) {
        validateRefreshToken(refreshToken);

        Long memberId = Long.valueOf(jwtUtil.getMemberId(refreshToken));
        MemberRole role = jwtUtil.getRole(refreshToken);

        Member member = memberAccountService.findMemberById(memberId);

        String newAccessToken = jwtUtil.createAccessToken(
                ACCESS_TOKEN_CATEGORY,
                String.valueOf(memberId),
                role.name()
        );

        String newRefreshToken = jwtUtil.createRefreshToken(
                REFRESH_TOKEN_CATEGORY,
                String.valueOf(memberId),
                role.name()
        );

        refreshTokenRepository.deleteByRefreshToken(refreshToken);
        saveOrUpdateRefreshToken(member, newRefreshToken);

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    /**
     * RefreshToken 유효성 및 서버 저장 여부 검증.
     */
    public void validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("RefreshToken이 존재하지 않습니다.");
        }

        if (jwtUtil.isExpired(refreshToken)) {
            throw new IllegalArgumentException("RefreshToken이 만료되었습니다.");
        }

        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("RefreshToken 타입이 아닙니다.");
        }

        if (!refreshTokenRepository.existsByRefreshToken(refreshToken)) {
            throw new EntityNotFoundException("서버에 저장된 RefreshToken이 존재하지 않습니다.");
        }
    }

    /**
     * 기존 RefreshToken이 있으면 갱신하고, 없으면 새로 저장한다.
     */
    private void saveOrUpdateRefreshToken(Member member, String refreshToken) {
        LocalDateTime expirationDateTime =
                LocalDateTime.now().plusSeconds(REFRESH_TOKEN_EXPIRATION_SECONDS);

        refreshTokenRepository.findByMember(member)
                .ifPresentOrElse(
                        existedRefreshToken ->
                                updateRefreshToken(existedRefreshToken, refreshToken, expirationDateTime),
                        () ->
                                createRefreshToken(member, refreshToken, expirationDateTime)
                );
    }

    private void updateRefreshToken(
            RefreshToken refreshTokenEntity,
            String refreshToken,
            LocalDateTime expirationDateTime
    ) {
        RefreshRequest request = RefreshRequest.createRefreshDto(refreshToken, expirationDateTime);
        refreshTokenEntity.updateRefreshToken(request);
    }

    private void createRefreshToken(
            Member member,
            String refreshToken,
            LocalDateTime expirationDateTime
    ) {
        RefreshToken newRefreshToken = new RefreshToken(member, refreshToken, expirationDateTime);
        refreshTokenRepository.save(newRefreshToken);
    }
}