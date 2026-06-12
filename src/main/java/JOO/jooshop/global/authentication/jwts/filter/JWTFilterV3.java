package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.dto.CustomMemberDto;
import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.authentication.jwts.utils.TokenResolver;
import JOO.jooshop.members.entity.enums.MemberRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * 요청마다 AccessToken을 검증해,
 * 현재 사용자를 SecurityContext에 인증된 사용자로 등록한다.

 * 핵심 역할
 * 쿠키에서 AccessToken 추출
 * JWT 만료 확인
 * Redis blacklist 확인
 * Member 조회
 * Authentication 생성
 * SecurityContextHolder에 저장

 * 흐름
  요청
  → JWTFilterV3
  → AccessToken 추출
  → JWT 검증
  → memberId 추출
  → Member 조회
  → Authentication 생성
  → SecurityContext 저장
  → Controller 진입
 */
@Slf4j
@RequiredArgsConstructor
public class JWTFilterV3 extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final JWTUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain
    ) throws ServletException, IOException {

        Optional<String> headerToken = TokenResolver.resolveTokenFromHeader(request);
        Optional<String> cookieToken = TokenResolver.resolveTokenFromCookie(request, ACCESS_TOKEN_COOKIE_NAME);

        String accessToken = headerToken.or(() -> cookieToken).orElse(null);

        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (isBlacklisted(accessToken)) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "로그아웃 처리된 토큰입니다.");
            return;
        }

        if (isInvalidToken(accessToken)) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 Access Token입니다.");
            return;
        }

        try {
            Authentication authentication = createAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT 인증 성공. memberId={}", jwtUtil.getMemberId(accessToken));

            filterChain.doFilter(request, response);

        } catch (BadCredentialsException e) {
            SecurityContextHolder.clearContext();
            log.warn("JWT 인증 객체 생성 실패: {}", e.getMessage());
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, e.getMessage());

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.error("JWT 인증 처리 중 예외 발생", e);
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "JWT 인증 처리에 실패했습니다.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return uri.startsWith("/css")
                || uri.startsWith("/js")
                || uri.startsWith("/Images")
                || uri.equals("/login")
                || uri.startsWith("/api/v1/reissue")
                || !uri.startsWith("/api");
    }

    private boolean isBlacklisted(String accessToken) {
        String key = BLACKLIST_PREFIX + accessToken;
        return redisTemplate.hasKey(key);
    }

    private boolean isInvalidToken(String accessToken) {
        return !jwtUtil.validateToken(accessToken) || jwtUtil.isExpired(accessToken);
    }

    private Authentication createAuthentication(String accessToken) {
        try {
            Long memberId = Long.valueOf(jwtUtil.getMemberId(accessToken));
            MemberRole role = jwtUtil.getRole(accessToken);

            CustomMemberDto memberDto = CustomMemberDto.minimal(memberId, role);
            CustomUserDetails userDetails = new CustomUserDetails(memberDto);

            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

        } catch (Exception e) {
            throw new BadCredentialsException("JWT 인증 객체 생성에 실패했습니다.", e);
        }
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", status.name(),
                "message", message
        ));
    }
}