package JOO.jooshop.global.authentication.jwts.utils;

import JOO.jooshop.members.entity.enums.MemberRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 문자열 생성, 파싱, 검증을 담당하는 저수준 유틸 컴포넌트.
 * 언제 어떤 토큰을 발급하고 저장할지는 TokenService가 결정한다.
 */
@Component
@Slf4j
public class JWTUtil {

    private static final String MEMBER_ID_KEY = "memberId";
    private static final String CATEGORY_KEY = "category";
    private static final String ROLE_KEY = "role";
    private static final String EMAIL_KEY = "email";

    // 외부로 노출하지 않는 내부 고정값
    private static final String ACCESS_CATEGORY = "access";
    private static final String REFRESH_CATEGORY = "refresh";

    private static final long ACCESS_TOKEN_EXPIRATION_SECONDS = 60L * 30;
    private static final long EMAIL_TOKEN_EXPIRATION_SECONDS = 60L * 15;

    @Value("${spring.jwt.secret}")
    private String jwtSecret;

    // TokenService가 DB 만료 시간과 동기화하기 위해 참조
    @Value("${spring.jwt.refresh-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    private SecretKey secretKey;

    /**
     * Base64 Secret 문자열을 JWT 서명용 SecretKey로 초기화한다.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT SecretKey 로딩 완료.");
    }

    /**
     * Access Token을 생성한다. category는 내부에서 "access"로 고정된다.
     */
    public String createAccessToken(String memberId, String role) {
        return createToken(ACCESS_CATEGORY, memberId, normalizeRole(role), ACCESS_TOKEN_EXPIRATION_SECONDS);
    }

    /**
     * Refresh Token을 생성한다. category는 내부에서 "refresh"로 고정된다.
     */
    public String createRefreshToken(String memberId, String role) {
        return createToken(REFRESH_CATEGORY, memberId, normalizeRole(role), refreshTokenExpirationSeconds);
    }

    /**
     * 이메일 인증 토큰을 생성한다.
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
     * DB RefreshToken 만료 시간 동기화를 위해 설정값을 노출한다.
     */
    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    /**
     * JWT에서 memberId 클레임을 추출한다.
     */
    public String getMemberId(String token) {
        return parseToken(token).get(MEMBER_ID_KEY, String.class);
    }

    /**
     * JWT에서 category 클레임을 추출한다.
     */
    public String getCategory(String token) {
        return parseToken(token).get(CATEGORY_KEY, String.class);
    }

    /**
     * JWT에서 role 클레임을 추출해 MemberRole enum으로 변환한다.
     */
    public MemberRole getRole(String token) {
        String role = parseToken(token).get(ROLE_KEY, String.class);
        return MemberRole.valueOf(removeRolePrefix(role));
    }

    /**
     * JWT의 만료 시각을 반환한다.
     */
    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    /**
     * 이메일 인증 토큰에서 이메일 주소를 추출한다.
     */
    public String getEmailFromToken(String token) {
        return parseToken(token).get(EMAIL_KEY, String.class);
    }

    /**
     * JWT의 서명, 구조, 만료 여부를 통합 검증한다.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
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

    /**
     * 전달받은 JWT가 Access Token인지 확인한다.
     */
    public boolean isAccessToken(String token) {
        try {
            return ACCESS_CATEGORY.equals(getCategory(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 전달받은 JWT가 Refresh Token인지 확인한다.
     */
    public boolean isRefreshToken(String token) {
        try {
            return REFRESH_CATEGORY.equals(getCategory(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * JWT가 현재 시간 기준으로 만료되었는지 확인한다.
     */
    public boolean isExpired(String token) {
        try {
            return getExpiration(token).before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * category, memberId, role, 만료 시간으로 JWT 문자열을 생성한다.
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
     * JWT 문자열을 검증하고 Claims를 반환한다.
     */
    private Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("JWT 토큰이 비어 있습니다.");
        }

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token.trim())
                .getPayload();
    }

    /**
     * 현재 시간에 만료 초를 더해 만료 Date를 생성한다.
     */
    private Date createExpiryDate(long expirationSeconds) {
        return Date.from(
                LocalDateTime.now()
                        .plusSeconds(expirationSeconds)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
        );
    }

    /**
     * role이 비어있는지 검증하고 ROLE_ 접두사를 제거한다.
     */
    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("권한 정보가 비어 있습니다.");
        }

        return removeRolePrefix(role);
    }

    /**
     * ROLE_ 접두사가 있으면 제거한다.
     */
    private String removeRolePrefix(String role) {
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }
}