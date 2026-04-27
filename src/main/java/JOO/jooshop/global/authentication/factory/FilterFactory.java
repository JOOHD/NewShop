package JOO.jooshop.global.authentication.factory;

import JOO.jooshop.global.authentication.jwts.filter.JWTFilterV3;
import JOO.jooshop.global.authentication.jwts.filter.LoginFilter;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import JOO.jooshop.members.service.MemberAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Component;

/**
 * 인증 필터 생성을 담당하는 Factory 클래스입니다.
 *
 * 역할:
 * - LoginFilter 생성
 * - JWTFilterV3 생성
 *
 * 목적:
 * - SecurityConfig에서 직접 new 키워드로 필터를 생성하지 않도록 분리합니다.
 * - 필터 생성에 필요한 의존성을 한 곳에서 관리합니다.
 * - SecurityConfig는 보안 정책 설정에만 집중하도록 합니다.
 */
@Component
@RequiredArgsConstructor
public class FilterFactory {

    private static final String API_LOGIN_URL = "/api/login";

    private final ObjectMapper objectMapper;
    private final JWTUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * API 로그인 요청을 처리하는 LoginFilter를 생성합니다.
     *
     * 처리 URL:
     * - POST /api/login
     *
     * 역할:
     * - username/password 인증 시도
     * - 인증 성공 시 Access Token / Refresh Token 발급
     * - Refresh Token 저장소 저장
     */
    public LoginFilter createLoginFilter(
            AuthenticationManager authenticationManager,
            MemberAccountService memberService
    ) {
        LoginFilter loginFilter = new LoginFilter(
                authenticationManager,
                objectMapper,
                memberService,
                jwtUtil,
                refreshTokenRepository
        );

        loginFilter.setFilterProcessesUrl(API_LOGIN_URL);
        return loginFilter;
    }

    /**
     * JWT 인증 필터를 생성합니다.
     *
     * 역할:
     * - 요청 쿠키 또는 헤더에서 JWT 추출
     * - JWT 유효성 검증
     * - Redis 블랙리스트 확인
     * - 인증 성공 시 SecurityContext에 Authentication 저장
     */
    public JWTFilterV3 createJWTFilter(MemberAccountService memberService) {
        return new JWTFilterV3(jwtUtil, redisTemplate, memberService);
    }
}