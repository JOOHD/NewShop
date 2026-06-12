package JOO.jooshop.global.authentication.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * 인증/인가 실패 시 공통 에러 응답 형식.
 * FormLoginFailureHandler, OAuth2LoginFailureHandler,
 * CustomLogoutFilter 등에서 일관된 형식으로 사용한다.
 */
@Getter
public class AuthErrorResponse {

    private final int status;
    private final String error;
    private final String message;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private final LocalDateTime timestamp;

    private AuthErrorResponse(HttpStatus status, String error, String message) {
        this.status = status.value();
        this.error = error;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public static AuthErrorResponse of(HttpStatus status, String error, String message) {
        return new AuthErrorResponse(status, error, message);
    }
}