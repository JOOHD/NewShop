package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.dto.CustomMemberDto;
import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.authentication.jwts.utils.TokenResolver;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.enums.MemberRole;
import JOO.jooshop.members.service.MemberAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.data.redis.core.RedisTemplate;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class JWTFilterV3 extends OncePerRequestFilter {

    /**
     * 2025.08.25 리팩토링
     * 1. 헤더 + 쿠키 토큰 지원 -> 브라우저 cookie에서 읽어 인증 가능
     * 2. 블랙리스트 / 만료 토큰 즉시 JSON 응답 -> 클라이언트가  403/401을 바로 인지
     * 3. Role 로그 출력 -> JWT Role vs Security Role 매칭 확인 가능
     * 4. sendJsonResponse 메서드로 중복 코드 제거
     */

    private final JWTUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final MemberAccountService memberService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 요청 URI, HTTP Method 로깅
        log.info("[JWTFilter] 요청 URI: {}", request.getRequestURI());
        log.info("[JWTFilter] HTTP Method: {}", request.getMethod());

        // 1) 쿠키 상세 로그
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                log.info("[JWTFilter] 쿠키: {}={}", cookie.getName(), cookie.getValue());
            }
        } else {
            log.info("[JWTFilter] 쿠키 없음");
        }

        // 2) 토큰 가져오기: 헤더 우선, 없으면 쿠키
        Optional<String> authorizationOpt = TokenResolver.resolveTokenFromHeader(request);
        Optional<String> accessTokenCookieOpt = TokenResolver.resolveTokenFromCookie(request, "accessToken");
        String accessToken = authorizationOpt.or(() -> accessTokenCookieOpt).orElse(null);

        log.info("[JWTFilter] Authorization 헤더: {}", authorizationOpt.orElse("없음"));
        log.info("[JWTFilter] accessToken 쿠키: {}", accessTokenCookieOpt.orElse("없음"));

        // 3) AccessToken 없으면 다음 필터로
        if (accessToken == null) {
            log.warn("[JWTFilter] AccessToken 없음 → 인증 스킵");
            filterChain.doFilter(request, response);
            return;
        }

        // 4) 블랙리스트 체크
        String blacklistValue = redisTemplate.opsForValue().get("blacklist:" + accessToken);
        log.info("[JWTFilter] 블랙리스트 조회값: {}", blacklistValue);
        if (blacklistValue != null) {
            log.warn("[JWTFilter] 블랙리스트 토큰: {}", accessToken);
            sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN, "블랙리스트 토큰입니다.");
            return;
        }

        // 5) 만료 토큰 체크
        boolean expired = jwtUtil.isExpired(accessToken);
        log.info("[JWTFilter] 토큰 만료 여부: {}", expired);
        if (expired) {
            log.warn("[JWTFilter] AccessToken 만료됨: {}", accessToken);
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "AccessToken 만료됨");
            return;
        }

        // 6) 토큰 유효성 체크
        boolean valid = jwtUtil.validateToken(accessToken);
        log.info("[JWTFilter] 토큰 유효성: {}", valid);
        if (!valid) {
            log.warn("[JWTFilter] 유효하지 않은 토큰: {}", accessToken);
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰");
            return;
        }

        // 7) JWT 내부 정보 로그 (MemberId, Role)
        String memberId = jwtUtil.getMemberId(accessToken);
        MemberRole role = jwtUtil.getRole(accessToken);
        log.info("[JWTFilter] 토큰 MemberId: {}, Role: {}", memberId, role);

        // 8) 인증 객체 생성 및 SecurityContext 저장
        try {
            Authentication authToken = getAuthentication(accessToken);
            if (authToken != null) {
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("[JWTFilter] SecurityContextHolder 인증 완료: {}", authToken.getPrincipal());
                log.info("[JWTFilter] 권한: {}", authToken.getAuthorities());
            } else {
                log.warn("[JWTFilter] Authentication 객체 생성 실패");
            }
        } catch (Exception e) {
            log.error("[JWTFilter] 인증 실패 예외: {}", e.getMessage(), e);
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "토큰 인증 실패");
            return;
        }

        filterChain.doFilter(request, response);
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/css") || uri.startsWith("/js") || uri.startsWith("/Images") || uri.equals("/login") || !uri.startsWith("/api");
    }

    /** AccessToken → Authentication 생성 */
    private Authentication getAuthentication(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new BadCredentialsException("유효하지 않은 토큰입니다.");
        }

        String email = jwtUtil.getMemberId(token);
        Member member = memberService.findMemberByEmail(email);

        CustomMemberDto customMemberDto = CustomMemberDto.createCustomMember(member);
        CustomUserDetails customUserDetails = new CustomUserDetails(customMemberDto);

        return new UsernamePasswordAuthenticationToken(
                customUserDetails,
                null,
                customUserDetails.getAuthorities()
        );
    }

    /** JSON 응답 편의 메서드 */
    private void sendJsonResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
