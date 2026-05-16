package JOO.jooshop.global.authentication.factory;

import JOO.jooshop.global.authentication.jwts.filter.JWTFilterV3;
import JOO.jooshop.global.authentication.jwts.filter.LoginFilter;
import JOO.jooshop.global.authentication.jwts.service.TokenService;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.members.service.MemberAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Component;

/**
 * SecurityConfig가 직접 복잡한 필터 생성자를 호출하지 않도록 필터 생성 책임을 분리한다.

 * 핵심 역할
 * LoginFilter 생성
 * JWTFilterV3 생성
 * CustomLogoutFilter 생성
 * SecurityConfig 코드 단순화
 */
@Component
@RequiredArgsConstructor
public class FilterFactory {

    private static final String API_LOGIN_URL = "/api/login";

    private final ObjectMapper objectMapper;
    private final JWTUtil jwtUtil;
    private final TokenService tokenService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.secure:false}")
    private boolean secureCookie;

    /**
     * API 로그인 요청을 처리하는 LoginFilter를 생성한다.
     *
     * 처리 URL:
     * - POST /api/login
     *
     * 역할:
     * 이메일/비밀번호 인증 시도
     * 인증 성공 시 회원 상태 검증
     * TokenService를 통해 Access Token / Refresh Token 발급
     * 토큰을 Cookie에 저장
     */
    public LoginFilter createLoginFilter(
            AuthenticationManager authenticationManager,
            MemberAccountService memberAccountService
    ) {
        LoginFilter loginFilter = new LoginFilter(
                authenticationManager,
                objectMapper,
                memberAccountService,
                tokenService,
                secureCookie
        );

        loginFilter.setFilterProcessesUrl(API_LOGIN_URL);
        return loginFilter;
    }

    /**
     * JWT 인증 필터를 생성한다.
     *
     * 역할
     * 요청 Header 또는 Cookie에서 Access Token 추출
     * JWT 유효성 검증
     * Redis 블랙리스트 확인
     * 인증 성공 시 SecurityContext에 Authentication 저장
     *
     * 주의
     * JWTFilterV3는 MemberService/MemberAccountService를 직접 의존하지 않는다.
     * JWT 안의 Claim만 사용해서 최소 인증 객체를 생성한다.
     */
    public JWTFilterV3 createJWTFilter() {
        return new JWTFilterV3(
                jwtUtil,
                redisTemplate,
                objectMapper
        );
    }
}