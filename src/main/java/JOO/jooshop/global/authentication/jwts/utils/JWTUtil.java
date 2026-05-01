package JOO.jooshop.global.authentication.jwts.utils;

import JOO.jooshop.members.entity.enums.MemberRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 생성, 검증, Claim 파싱을 담당하는 유틸 클래스.
 * 토큰 저장/재발급 비즈니스 로직은 TokenService가 담당한다.
 */
@Component
@Slf4j
public class JWTUtil {

    /*
    ※ JWTUtil 클래스 역할 요약

    - JWT 생성: AccessToken / RefreshToken / Email 인증 토큰 발급
    - JWT 검증: 서명 및 만료 여부 확인
    - JWT 파싱: memberId, category, role 등 Claim 추출
    - 토큰 재발급: 만료된 AccessToken → RefreshToken 기반으로 재발급

    ※ 전체 사용 흐름

    1. 초기화: @PostConstruct → secretKey 로딩 (application.yml)
    2. 로그인 성공 시: AccessToken, RefreshToken 생성
    3. API 호출 시: validateToken() 으로 토큰 유효성 검증
    4. 토큰 파싱: getMemberId(), getCategory(), getRole() 등 Claim 추출
    5. 만료 확인: isExpired(), getExpiration()
    6. 재발급: reissueAccessToken(refreshToken)
    7. 로그아웃: getExpiration() → Redis 블랙리스트 저장
    */

    private static final String MEMBER_ID_KEY = "memberId";
    private static final String CATEGORY_KEY = "category";
    private static final String ROLE_KEY = "role";
    private static final String EMAIL_KEY = "email";

    private static final String ACCESS_CATEGORY = "access";
    private static final String REFRESH_CATEGORY = "refresh";

    private static final long ACCESS_TOKEN_EXPIRATION_SECONDS = 60L * 30;
    private static final long REFRESH_TOKEN_EXPIRATION_SECONDS = 60L * 60 * 24 * 7;
    private static final long EMAIL_TOKEN_EXPIRATION_SECONDS = 60L * 15;

    private SecretKey secretKey;

    @Value("${spring.jwt.secret}")
    private String jwtSecret;

    /**
     * Base64 Secret 문자열을 JWT 서명용 SecretKey 로 변환
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT SecretKey 로딩 완료 (base64).");
    }

    /**
     * AccessToken 생성
     */
    public String createAccessToken(String memberId, String role) {
        return createToken(ACCESS_CATEGORY, memberId, normalizeRole(role), ACCESS_TOKEN_EXPIRATION_SECONDS);
    }

    /**
     * RefreshToken 생성.
     */
    public String createRefreshToken(String memberId, String role) {
        return createToken(REFRESH_CATEGORY, memberId, normalizeRole(role), REFRESH_TOKEN_EXPIRATION_SECONDS);
    }

    /**
     * 이메일 인증 토큰 생성.
     */
    public String createEmailToken(String email) {
        Date now = new Date();
        Date expiry = createExpiryDate(EMAIL_TOKEN_EXPIRATION_SECONDS);

        return Jwts.builder()
                .claim(EMAIL_KEY, email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 공통 JWT 생성 메서드.
     */
    private String createToken(String category, String memberId, String role, long expirationSeconds) {
        Date now = new Date();
        Date expiry = createExpiryDate(expirationSeconds);

        return Jwts.builder()
                .claim(CATEGORY_KEY, category)
                .claim(MEMBER_ID_KEY, memberId)
                .claim(ROLE_KEY, role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * JWT 문자열에서 Claims(클레임 정보)를 파싱함
     */
    private Claims parseToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT 토큰이 비어 있습니다.");
        }
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
    }

    /**
     * JWT memberId 클레임 추출
     */
    public String getMemberId(String token) { return parseToken(token).get(MEMBER_ID_KEY, String.class); }

    /**
     * JWT category 클레임 추출
     */
    public String getCategory(String token) {
        return parseToken(token).get(CATEGORY_KEY, String.class);
    }

    /**
     * JWT role 클레임 추출 및 MemberRole enum으로 변환
     */
    public MemberRole getRole(String token) {
        String role = parseToken(token).get(ROLE_KEY, String.class);
        return MemberRole.valueOf(removeRolePrefix(role));
    }

    /**
     * JWT의 만료 시간(Expiration) 추출
     */
    public Date getExpiration(String accessToken) {
        return parseToken(accessToken).getExpiration();
    }

    /**
     * 이메일 인증 토큰에서 이메일 주소 추출
     */
    public String getEmailFromToken(String token) {
        return parseToken(token).get(EMAIL_KEY, String.class);
    }

    /**
     * JWT 서명/구조/만료 검증
     */
    public boolean validateToken(String token) {
        if (token == null || token.trim().isBlank()) {
            return false;
        }
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            return ACCESS_CATEGORY.equals(getCategory(token));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            return REFRESH_CATEGORY.equals(getCategory(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * JWT 만료 여부 확인
     */
    public boolean isExpired(String token) {
        try {
            return getExpiration(token).before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    private Date createExpiryDate(long expirationSeconds) {
        return Date.from(
                LocalDateTime.now()
                        .plusSeconds(expirationSeconds)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
        );
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("권한 정보가 비어 있습니다.");
        }
        return removeRolePrefix(role);
    }

    private String removeRolePrefix(String role) {
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }
}

