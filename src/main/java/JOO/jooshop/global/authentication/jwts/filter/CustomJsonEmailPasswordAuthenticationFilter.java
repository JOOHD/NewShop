package JOO.jooshop.global.authentication.jwts.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.util.Assert;

import org.springframework.lang.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public class CustomJsonEmailPasswordAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    /**
     * 전체 요약
     * 1. 클랑이언트 /login 경로로 POST 요청 (JSON)
     * 2. CustomJsonEamilPassword.. 가 요청을 가로챔
     * 3. StreamUtils.copyToString() 사용해 요청 본문을 String 으로 읽음
     * 4. ObjectMapper 로 Json을 Map<String, String> 형태로 변환
     * 5. email 과 password 값을 추출하여 UsernamePasswordAuthenticationToken 생성
     * 6. AuthenticationManager.authenticate()를 호출하여 인증 진행
     *      - 인증 성공 시 인증된 Authentication 객체 반환
     *      - 인증 실패 시 예외 발생
     */
    public static final String SPRING_SECURITY_FORM_USERNAME_KEY = "email";

    public static final String SPRING_SECURITY_FORM_PASSWORD_KEY = "password";

    private static final AntPathRequestMatcher DEFAULT_ANT_PATH_REQUEST_MATCHER = new AntPathRequestMatcher("/login",
            "POST");

    private static final String CONTENT_TYPE = "application/json"; // JSON 타입의 데이터로 오는 로그인 요청만 처리

    private String usernameParameter = SPRING_SECURITY_FORM_USERNAME_KEY;

    private String passwordParameter = SPRING_SECURITY_FORM_PASSWORD_KEY;

    private boolean postOnly = true;

    // private : 자식 클래스에서 직접 접근 불가능.
    // protected : 자식 클래스에서 직접 사용할 수 있다.
    protected final ObjectMapper objectMapper;

    public CustomJsonEmailPasswordAuthenticationFilter(AuthenticationManager authenticationManager, ObjectMapper objectMapper) {
        super(DEFAULT_ANT_PATH_REQUEST_MATCHER, authenticationManager);
        this.objectMapper = objectMapper;
    }



    /**
     * 인증 처리 메소드
     *
     * UsernamePasswordAuthenticationFilter와 동일하게 UsernamePasswordAuthenticationToken 사용
     * StreamUtils를 통해 request에서 messageBody(JSON) 반환
     * 요청 JSON Example
     * {
     *    "email" : "aaa@bbb.com"
     *    "password" : "test123"
     * }
     * 꺼낸 messageBody를 objectMapper.readValue()로 Map으로 변환 (Key : JSON의 키 -> email, password)
     * Map의 Key(email, password)로 해당 이메일, 패스워드 추출 후
     * UsernamePasswordAuthenticationToken의 파라미터 principal, credentials에 대입
     *
     * AbstractAuthenticationProcessingFilter(부모)의 getAuthenticationManager()로 AuthenticationManager 객체를 반환 받은 후
     * authenticate()의 파라미터로 UsernamePasswordAuthenticationToken 객체를 넣고 인증 처리
     * (여기서 AuthenticationManager 객체는 ProviderManager -> SecurityConfig에서 설정)
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException {
        if(request.getContentType() == null || !request.getContentType().equals(CONTENT_TYPE)  ) {
            throw new AuthenticationServiceException("Authentication Content-Type not supported: " + request.getContentType());
        }

        String messageBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);

//        Map<String, String> usernamePasswordMap = objectMapper.readValue(messageBody, Map.class);
        // 제네릭 타입을 new TypeReference 선언해줌으로써 컴파일시 제네릭타입으로 검증.
        Map<String, String> usernamePasswordMap = objectMapper.readValue(messageBody, new TypeReference<Map<String, String>>() {});

        String email = usernamePasswordMap.get(SPRING_SECURITY_FORM_USERNAME_KEY);
        String password = usernamePasswordMap.get(SPRING_SECURITY_FORM_PASSWORD_KEY);

        log.info("[로그인 요청] 파싱된 이메일: {}, 비밀번호: {}", email, password);
        log.info("[로그인 요청] 전체 요청 맵: {}", usernamePasswordMap);

        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(email, password);
        //principal 과 credentials 전달

        // this = CustomJsonEmailPasswordAuthenticationFilter 의 인스턴스를 가리킨다.
        // 즉, AbstractAuthentication... 을 상속, 부모 클래스의 메서드를 호출한다는 의미 (this 생략 가능)
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    /**
     * Enables subclasses to override the composition of the password, such as by
     * including additional values and a separator.
     * <p>
     * This might be used for example if a postcode/zipcode was required in addition to
     * the password. A delimiter such as a pipe (|) should be used to separate the
     * password and extended value(s). The <code>AuthenticationDao</code> will need to
     * generate the expected password in a corresponding manner.
     * </p>
     * @param request so that request attributes can be retrieved
     * @return the password that will be presented in the <code>Authentication</code>
     * request token to the <code>AuthenticationManager</code>
     */
    @Nullable
    protected String obtainPassword(HttpServletRequest request) {
        return request.getParameter(this.passwordParameter);
    }

    /**
     * Enables subclasses to override the composition of the username, such as by
     * including additional values and a separator.
     * @param request so that request attributes can be retrieved
     * @return the username that will be presented in the <code>Authentication</code>
     * request token to the <code>AuthenticationManager</code>
     */
    @Nullable
    protected String obtainUsername(HttpServletRequest request) {
        return request.getParameter(this.usernameParameter);
    }

    /**
     * Provided so that subclasses may configure what is put into the authentication
     * request's details property.
     * @param request that an authentication request is being created for
     * @param authRequest the authentication request object that should have its details
     * set
     */
    protected void setDetails(HttpServletRequest request, UsernamePasswordAuthenticationToken authRequest) {
        authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));
    }

    /**
     * Sets the parameter name which will be used to obtain the username from the login
     * request.
     * @param usernameParameter the parameter name. Defaults to "username".
     */
    public void setUsernameParameter(String usernameParameter) {
        Assert.hasText(usernameParameter, "Username parameter must not be empty or null");
        this.usernameParameter = usernameParameter;
    }

    /**
     * Sets the parameter name which will be used to obtain the password from the login
     * request..
     * @param passwordParameter the parameter name. Defaults to "password".
     */
    public void setPasswordParameter(String passwordParameter) {
        Assert.hasText(passwordParameter, "Password parameter must not be empty or null");
        this.passwordParameter = passwordParameter;
    }

}
