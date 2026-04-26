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

@Component
@RequiredArgsConstructor
public class FilterFactory {

    private final ObjectMapper objectMapper;
    private final JWTUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public LoginFilter createLoginFilter(AuthenticationManager authenticationManager, MemberAccountService memberService) {
        LoginFilter loginFilter = new LoginFilter(
                authenticationManager,
                objectMapper,
                memberService,
                jwtUtil,
                refreshTokenRepository
        );
        loginFilter.setFilterProcessesUrl("/api/login");
        return loginFilter;
    }

    public JWTFilterV3 createJWTFilter(MemberAccountService memberService) {
        return new JWTFilterV3(jwtUtil, redisTemplate, memberService);
    }
}
