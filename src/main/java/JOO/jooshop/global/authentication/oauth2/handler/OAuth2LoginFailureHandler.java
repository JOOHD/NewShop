package JOO.jooshop.global.authentication.oauth2.handler;

import JOO.jooshop.global.authentication.dto.AuthErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2 로그인 실패 시 공통 에러 형식으로 JSON 응답을 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        log.error("[OAuth2] 로그인 실패: {}", exception.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(response.getWriter(),
                AuthErrorResponse.of(HttpStatus.UNAUTHORIZED, "OAUTH2_LOGIN_FAILED", exception.getMessage())
        );
    }
}