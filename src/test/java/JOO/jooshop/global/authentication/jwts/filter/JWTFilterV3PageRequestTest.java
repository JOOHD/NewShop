package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.members.entity.enums.MemberRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 페이지(화면) 요청에서도 JWT 쿠키를 읽는지 검증한다.
 * 배경: 서버 재시작으로 세션이 사라지면 /tempOrder 등 페이지가 로그인 화면으로 튕기던 문제.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JWTFilterV3PageRequestTest {

    @Mock JWTUtil jwtUtil;
    @Mock RedisTemplate<String, String> redisTemplate;

    JWTFilterV3 filter;

    @BeforeEach
    void setUp() {
        filter = new JWTFilterV3(jwtUtil, redisTemplate, new ObjectMapper());
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String uri, String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        if (token != null) {
            req.setCookies(new Cookie("accessToken", token));
        }
        return req;
    }

    private void validToken(String token) {
        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.isExpired(token)).thenReturn(false);
        when(jwtUtil.getMemberId(token)).thenReturn("1");
        when(jwtUtil.getRole(token)).thenReturn(MemberRole.USER);
    }

    @Test
    @DisplayName("페이지 요청 + 유효한 JWT 쿠키 → 세션 없이도 인증된다")
    void pageRequest_validToken_authenticated() throws Exception {
        validToken("good");
        MockHttpServletRequest req = request("/tempOrder", "good");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();   // 체인 진행
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("페이지 요청 + 만료된 JWT → JSON 에러 없이 비인증으로 통과 (Security가 /login 으로 보냄)")
    void pageRequest_expiredToken_passesAnonymously() throws Exception {
        when(jwtUtil.validateToken("old")).thenReturn(true);
        when(jwtUtil.isExpired("old")).thenReturn(true);
        MockHttpServletRequest req = request("/tempOrder", "old");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("페이지 요청 + 블랙리스트 토큰 → 인증하지 않고 통과")
    void pageRequest_blacklisted_passesAnonymously() throws Exception {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        MockHttpServletRequest req = request("/tempOrder", "logged-out");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("API 요청 + 만료된 JWT → 기존대로 401 JSON")
    void apiRequest_expiredToken_401() throws Exception {
        when(jwtUtil.validateToken("old")).thenReturn(true);
        when(jwtUtil.isExpired("old")).thenReturn(true);
        MockHttpServletRequest req = request("/api/v1/order/confirm", "old");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();      // 체인 중단
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("API 요청 + 블랙리스트 토큰 → 기존대로 403")
    void apiRequest_blacklisted_403() throws Exception {
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        MockHttpServletRequest req = request("/api/v1/cart", "logged-out");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("토큰이 없으면 페이지/API 모두 그대로 통과 (인가는 Security가 판단)")
    void noToken_passes() throws Exception {
        MockHttpServletRequest req = request("/tempOrder", null);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("필터 제외 경로: 정적 리소스, 로그인/로그아웃/OAuth2 진입점, reissue")
    void shouldNotFilter_excludedPaths() {
        assertThat(filter.shouldNotFilter(request("/css/a.css", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/js/a.js", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/Images/a.png", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/login", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/formLogin", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/logout", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/oauth2/authorization/kakao", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/login/oauth2/code/kakao", null))).isTrue();
        assertThat(filter.shouldNotFilter(request("/api/v1/reissue", null))).isTrue();
    }

    @Test
    @DisplayName("필터 대상 경로: 페이지(/tempOrder, /cart)도 이제 JWT를 읽는다")
    void shouldNotFilter_pagesAreFiltered() {
        assertThat(filter.shouldNotFilter(request("/tempOrder", null))).isFalse();
        assertThat(filter.shouldNotFilter(request("/cart", null))).isFalse();
        assertThat(filter.shouldNotFilter(request("/api/v1/order/confirm", null))).isFalse();
    }
}
