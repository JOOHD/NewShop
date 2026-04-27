package JOO.jooshop.global.authentication.jwts.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JSON 로그인 요청 DTO.
 */
@Getter
@NoArgsConstructor
public class LoginRequest {

    private String email;
    private String password;
}
