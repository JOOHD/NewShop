package JOO.jooshop.global.authentication.jwts.service;

import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 인증 쿠키 조회를 담당하는 서비스.
 * 쿠키 이름 정책을 한 곳에서 관리한다.
 */
@Service
@RequiredArgsConstructor
public class CookieService {

    public static final String ACCESS_TOKEN_COOKIE = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE = "refreshAuthorization";

    public String getAccessToken(HttpServletRequest request) {
        return CookieUtil.getCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    public String getRefreshToken(HttpServletRequest request) {
        return CookieUtil.getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }
}