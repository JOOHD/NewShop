package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.dto.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

/**
 * JSON 기반 로그인 요청을 Spring Security 인증 토큰으로 변환하는 필터.
 * 요청 파싱과 AuthenticationManager 위임만 담당한다.
 */
@Slf4j
public class CustomJsonEmailPasswordAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private static final AntPathRequestMatcher LOGIN_REQUEST_MATCHER =
            new AntPathRequestMatcher("/login", "POST");

    private final ObjectMapper objectMapper;

    public CustomJsonEmailPasswordAuthenticationFilter(
            AuthenticationManager authenticationManager,
            ObjectMapper objectMapper
    ) {
        super(LOGIN_REQUEST_MATCHER, authenticationManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public Authentication attemptAuthentication(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws AuthenticationException, IOException {

        validateContentType(request);

        LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                );

        return getAuthenticationManager().authenticate(authToken);
    }

    private void validateContentType(HttpServletRequest request) {
        String contentType = request.getContentType();

        if (contentType == null || !contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
            throw new AuthenticationServiceException("지원하지 않는 로그인 요청 Content-Type입니다: " + contentType);
        }
    }
}