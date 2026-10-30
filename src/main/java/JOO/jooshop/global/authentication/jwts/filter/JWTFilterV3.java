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

    // 요청의 JWT를 검증(블랙리스트/유효성/만료)하고 인증 정보를 SecurityContext에 설정
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

        // 페이지(화면) 요청은 JSON 에러를 쓰지 않는다.
        // 토큰이 없거나 잘못되면 인증 없이 통과시키고, 보호된 경로면 Security가 /login 으로 리다이렉트한다.
        boolean pageRequest = isPageRequest(request);

        if (isBlacklisted(accessToken)) {
            if (pageRequest) {
                filterChain.doFilter(request, response);
                return;
            }
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "로그아웃 처리된 토큰입니다.");
            return;
        }

        if (isInvalidToken(accessToken)) {
            if (pageRequest) {
                filterChain.doFilter(request, response);
                return;
            }
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 Access Token입니다.");
            return;
        }

        try {
            Authentication authentication = createAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT 인증 성공. memberId={}", jwtUtil.getMemberId(accessToken));

        } catch (BadCredentialsException e) {
            SecurityContextHolder.clearContext();
            log.warn("JWT 인증 객체 생성 실패: {}", e.getMessage());
            if (!pageRequest) {
                writeErrorResponse(response, HttpStatus.UNAUTHORIZED, e.getMessage());
                return;
            }

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.error("JWT 인증 처리 중 예외 발생", e);
            if (!pageRequest) {
                writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "JWT 인증 처리에 실패했습니다.");
                return;
            }
        }

        // 인증 성공/페이지 요청 실패 모두 다음 필터로 진행 (체인 실행을 try 밖으로 빼서 하위 예외가 JWT 오류로 오인되지 않게 함)
        filterChain.doFilter(request, response);
    }

    // /api 로 시작하지 않으면 화면 요청
    private boolean isPageRequest(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api");
    }

    // 정적 리소스, 로그인/로그아웃 진입점 등 JWT 검사가 필요 없는 경로는 필터 제외
    // (페이지 요청도 JWT 쿠키를 읽는다: 서버 재시작으로 세션이 사라져도 로그인 상태 유지)
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();

        return uri.startsWith("/css")
                || uri.startsWith("/js")
                || uri.startsWith("/Images")
                || uri.equals("/favicon.ico")
                || uri.equals("/login")
                || uri.equals("/formLogin")
                || uri.equals("/logout")
                || uri.startsWith("/oauth2/")
                || uri.startsWith("/login/oauth2/")
                || uri.startsWith("/api/v1/reissue");
    }

    // 로그아웃된(블랙리스트) 토큰인지 Redis에서 확인
    private boolean isBlacklisted(String accessToken) {
        String key = BLACKLIST_PREFIX + accessToken;
        return redisTemplate.hasKey(key);
    }

    // 토큰이 유효하지 않거나 만료되었는지 여부
    private boolean isInvalidToken(String accessToken) {
        return !jwtUtil.validateToken(accessToken) || jwtUtil.isExpired(accessToken);
    }

    // 토큰의 회원 ID/권한으로 Authentication 객체 생성
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

    // JWT 오류를 JSON 형태로 응답
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