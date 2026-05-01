package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.authentication.jwts.utils.TokenResolver;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * 로그아웃 요청을 처리하는 필터.
 * AccessToken 블랙리스트 등록, RefreshToken 삭제, 인증 쿠키 제거를 담당한다.
 */
@Slf4j
@RequiredArgsConstructor
public class CustomLogoutFilter extends GenericFilterBean {

    private final JWTUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.secure:false")
    private boolean secureCookie;

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

        Optional<String> refreshToken = TokenResolver.resolveTokenFromCookie(httpRequest, "refreshAuthorization");

        accessToken.ifPresent(this::blacklistAccessToken);
        refreshToken.ifPresent(this::deleteRefreshToken);

        deleteAuthCookies(httpResponse);
        writeLogoutResponse(httpResponse);

        log.info("로그아웃 완료");
    }

    private boolean isLogoutRequest(HttpServletRequest request) {
        return "/logout".equals(request.getRequestURI())
                && "POST".equalsIgnoreCase(request.getMethod());
    }

    private void blacklistAccessToken(String accessToken) {
        try {
            Date expiration = jwtUtil.getExpiration(accessToken);
            long remainSeconds = (expiration.getTime() - System.currentTimeMillis());

            if (remainSeconds > 0) {
                redisTemplate.opsForValue()
                        .set("blacklist:" + accessToken, "logout", remainSeconds);
            }
        } catch (Exception e) {
            log.warn("AccessToken 블랙리스트 등록 실패: {}", e.getMessage());
        }
    }

    private void deleteRefreshToken(String refreshToken) {
        if (refreshTokenRepository.existsByRefreshToken(refreshToken)) {
            refreshTokenRepository.deleteByRefreshToken(refreshToken);
            return;
        }
    }

    private void deleteAuthCookies(HttpServletResponse response) {
        if (secureCookie) {
            CookieUtil.deleteSecureCookie(response, "accessToken");
            CookieUtil.deleteSecureCookie(response, "refreshAuthorization");
            return;
        }

        CookieUtil.deleteLocalCookie(response, "accessToken");
        CookieUtil.deleteLocalCookie(response, "refreshAuthorization");
    }

    private void writeLogoutResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(response.getWriter(), Map.of(
                "message", "로그아웃에 성공했습니다."
        ));
    }
}
