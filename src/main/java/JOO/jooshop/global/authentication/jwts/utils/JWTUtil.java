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
 * 로그인/재발급 상황에서 JWT를 발급하고, RefreshToken을 저장·갱신하는 인증 흐름 서비스
 
 * JWTUtil이 토큰 문자열을 만드는 도구라면,
 * TokenService는 언제 어떤 토큰을 만들고, 어디에 저장하고, 어떤 응답으로 돌려줄지 결정하는 서비스
 */
@Component
@Slf4j
public class JWTUtil {

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
     * Base64 Secret 문자열을 JWT 서명용 SecretKey로 변환한다.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT SecretKey 로딩 완료 (base64).");
    }

    /**
     * 회원 식별자와 권한 정보를 담은 AccessToken을 생성한다.
     */
    public String createAccessToken(String memberId, String role) {
        return createToken(ACCESS_CATEGORY, memberId, normalizeRole(role), ACCESS_TOKEN_EXPIRATION_SECONDS);
    }

    /**
     * 회원 식별자와 권한 정보를 담은 RefreshToken을 생성한다.
     */
    public String createRefreshToken(String memberId, String role) {
        return createToken(REFRESH_CATEGORY, memberId, normalizeRole(role), REFRESH_TOKEN_EXPIRATION_SECONDS);
    }

    /**
     * 이메일 인증에 사용할 이메일 인증 토큰을 생성한다.
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
     * 토큰 category, memberId, role, 만료 시간을 기반으로 JWT 문자열을 생성한다.
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
     * JWT 문자열을 검증한 뒤 Claims 정보를 추출한다.
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
     * JWT에서 memberId 클레임 값을 추출한다.
     */
    public String getMemberId(String token) {
        return parseToken(token).get(MEMBER_ID_KEY, String.class);
    }

    /**
     * JWT에서 category 클레임 값을 추출한다.
     */
    public String getCategory(String token) {
        return parseToken(token).get(CATEGORY_KEY, String.class);
    }

    /**
     * JWT에서 role 클레임 값을 추출하고 MemberRole enum으로 변환한다.
     */
    public MemberRole getRole(String token) {
        String role = parseToken(token).get(ROLE_KEY, String.class);
        return MemberRole.valueOf(removeRolePrefix(role));
    }

    /**
     * JWT에서 만료 시간 정보를 추출한다.
     */
    public Date getExpiration(String accessToken) {
        return parseToken(accessToken).getExpiration();
    }

    /**
     * 이메일 인증 토큰에서 이메일 주소를 추출한다.
     */
    public String getEmailFromToken(String token) {
        return parseToken(token).get(EMAIL_KEY, String.class);
    }

    /**
     * JWT의 서명, 구조, 만료 여부를 검증한다.
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

    /**
     * 전달받은 JWT가 AccessToken인지 확인한다.
     */
    public boolean isAccessToken(String token) {
        try {
            return ACCESS_CATEGORY.equals(getCategory(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 전달받은 JWT가 RefreshToken인지 확인한다.
     */
    public boolean isRefreshToken(String token) {
        try {
            return REFRESH_CATEGORY.equals(getCategory(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * JWT가 현재 시간을 기준으로 만료되었는지 확인한다.
     */
    public boolean isExpired(String token) {
        try {
            return getExpiration(token).before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 현재 시간에 만료 초를 더해 JWT 만료 시간을 생성한다.
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
     * 권한 문자열이 비어 있는지 검증하고 ROLE_ 접두사를 제거한다.
     */
    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("권한 정보가 비어 있습니다.");
        }

        return removeRolePrefix(role);
    }

    /**
     * ROLE_ 접두사가 붙은 권한 문자열에서 접두사를 제거한다.
     */
    private String removeRolePrefix(String role) {
        return role.startsWith("ROLE_") ? role.substring(5) : role;
    }
}