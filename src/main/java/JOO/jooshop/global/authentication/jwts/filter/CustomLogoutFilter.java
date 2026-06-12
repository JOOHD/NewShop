package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.dto.AuthErrorResponse;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.authentication.jwts.utils.TokenCookieWriter;
import JOO.jooshop.global.authentication.jwts.utils.TokenResolver;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 로그아웃 요청(POST /logout)을 처리한다.
 * AccessToken을 Redis blacklist에 등록하고,
 * RefreshToken DB 삭제 및 인증 쿠키를 제거한다.
 */
@Slf4j
@RequiredArgsConstructor
public class CustomLogoutFilter extends GenericFilterBean {

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String LOGOUT_URI = "/logout";

    private final JWTUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;
    private final TokenCookieWriter tokenCookieWriter;  // 쿠키 삭제 위임

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain filterChain
    ) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!isLogoutRequest(httpRequest)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<String> accessToken = TokenResolver.resolveTokenFromHeader(httpRequest)
                .or(() -> TokenResolver.resolveTokenFromCookie(httpRequest, "accessToken"));

        Optional<String> refreshToken = TokenResolver.resolveTokenFromCookie(
                httpRequest, "refreshAuthorization"
        );

        accessToken.ifPresent(this::blacklistAccessToken);
        refreshToken.ifPresent(this::deleteRefreshToken);

        // Form Login으로 생성된 세션 무효화 (Spring Security .logout() 없이 처리)
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        tokenCookieWriter.clear(httpResponse);
        writeLogoutResponse(httpResponse);

        log.info("로그아웃 완료");
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        return LOGOUT_URI.equals(request.getRequestURI())
                && "POST".equalsIgnoreCase(request.getMethod());
    }

    private void blacklistAccessToken(String accessToken) {
        try {
            Date expiration = jwtUtil.getExpiration(accessToken);
            long remainMillis = expiration.getTime() - System.currentTimeMillis();

            if (remainMillis > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + accessToken,
                        "logout",
                        remainMillis,
                        TimeUnit.MILLISECONDS  // 단위 명시
                );
            }
        } catch (Exception e) {
            log.warn("AccessToken 블랙리스트 등록 실패: {}", e.getMessage());
        }
    }

    private void deleteRefreshToken(String refreshToken) {
        // existsByRefreshToken 체크 불필요 — 없으면 0건 삭제로 끝남
        refreshTokenRepository.deleteByRefreshToken(refreshToken);
    }

    private void writeLogoutResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of(
                "message", "로그아웃에 성공했습니다."
        ));
    }
}