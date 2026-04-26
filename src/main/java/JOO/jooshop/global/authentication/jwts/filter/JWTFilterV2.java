package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.entity.CustomUserDetails;
import JOO.jooshop.global.authentication.jwts.entity.CustomMemberDto;
import JOO.jooshop.global.authentication.jwts.service.CookieService;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JWTFilterV2 extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;
    private final CookieService cookieService;
    private final MemberRepository memberRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String refreshAuthorization = cookieService.getRefreshAuthorization(request);

        if (refreshAuthorization == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String refreshToken = refreshAuthorization.substring(7);  // "Bearer " 이후 부분만 처리
        if (!jwtUtil.validateToken(refreshToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (authorization != null && authorization.startsWith("Bearer ")) {
            String accessToken = authorization.split(" ")[1];

            if (jwtUtil.isExpired(accessToken)) {
                filterChain.doFilter(request, response);  // 만료된 토큰을 처리하는 부분
                return;
            }

            // authToken = new UsernamePasswordAuthenticationToken(jwtUtil.getMemberId(accessToken), null, Collections.emptyList());
            Authentication authToken = getAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }

    private Authentication getAuthentication(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new BadCredentialsException("유효하지 않은 토큰입니다.");
        }

        String memberId = jwtUtil.getMemberId(token);
        Member member = memberRepository.findById(Long.valueOf(memberId))
                .orElseThrow(() -> new BadCredentialsException("사용자를 찾을 수 없습니다."));

        // CustomMemberDto 객체 생성
        CustomMemberDto customMemberDto = CustomMemberDto.createCustomMember(member);
        // CustomUserDetails 객체 생성 -> OAuth2 authorities 가져오기
        CustomUserDetails customOAuth2User = new CustomUserDetails(customMemberDto);
        return new UsernamePasswordAuthenticationToken(customOAuth2User, null, customOAuth2User.getAuthorities());

    }
}