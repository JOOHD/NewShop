package JOO.jooshop.global.authentication.jwts.filter;

import JOO.jooshop.global.authentication.jwts.utils.CookieUtil;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.members.entity.Member;
import JOO.jooshop.members.entity.RefreshToken;
import JOO.jooshop.members.model.request.RefreshRequest;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import JOO.jooshop.members.service.MemberAccountService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class LoginFilter extends CustomJsonEmailPasswordAuthenticationFilter {

    /**
     * 전체 요약
     * 1. authentication.getName() 이메일 조회
     * 2. memberService.validateDuplicatedEmail(email) 로 회원 정보 조회
     * 3. 사용자 권한(Role) 정보 추출
     * 4. jwtUtil.createAccessToken() & jwtUtil.createRefreshToken() 을 사용해 JWT ACCESS/REFRESH 토큰 생성
     * 5. saveOrUpdateRefreshEntity() 호출 -> 리프레시 토큰을 DB에 저장/업데이트
     * 6. addResponseDataV3() 를 호출하여 엑세스 토큰을 JSON 으로 반환하고 리프레시 토큰을 쿠키에 저장.
     */
    private Long accessTokenExpirationPeriod = 3600L;

    private Long refreshTokenExpirationPeriod = 1209600L;

    private final MemberAccountService memberService;
    private final JWTUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final String CONTENT_TYPE = "application/json"; // JSON 타입의 데이터로 오는 로그인 요청만 처리

    public LoginFilter(AuthenticationManager authenticationManager, ObjectMapper objectMapper, MemberAccountService memberService, JWTUtil jwtUtil, RefreshTokenRepository refreshTokenRepository) {
        super(authenticationManager, objectMapper); // 초기화
        this.memberService = memberService;
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * 로그인 요청을 처리하고 인증 시도
     * @throws AuthenticationException
     * @throws IOException
     */
    @Override 
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException {
        log.info("[디버깅] attemptAuthentication 호출됨");

        try {
            if (request.getContentType() == null || !request.getContentType().equals(CONTENT_TYPE)) {
                throw new AuthenticationServiceException("Authentication Content-Type not supported: " + request.getContentType());
            }
            String messageBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            // 자바 8 이상부터, TypeReference 를 통해 원하는 형(Type)을 넣어주지 않으면 경고문이 뜸. NullPointException 등등 (ex) get("email"), readValue("meesage") 등등 . 읽어오지 못할 경우도 생기기 때문
            // Map<String, String> usernamePasswordMap = objectMapper.readValue(messageBody, Map.class);

            Map<String, String> usernamePasswordMap = objectMapper.readValue(messageBody, new TypeReference<Map<String, String>>() {});

            // 클라이언트 요청에서 email, password 추출
            String email = usernamePasswordMap.get("email");
            String password = usernamePasswordMap.get("password");

            // 사용자 정보에서 isCertifyByMail 필드 확인
            Member member = memberService.findMemberByEmail(email);
            if (!member.isCertifiedByEmail()) {
                throw new AuthenticationServiceException("Email is not certified yet.");
            }

            boolean isCertifyByMail = member.isCertifiedByEmail();
            if (!isCertifyByMail) {
                // 이메일이 인증되지 않은 경우 로그인 실패 처리
                throw new AuthenticationServiceException("Email is not certified yet. 이메일 인증이 되지 않았습니다. ");
            }

            // Principal(유저이메일), Credentials(비밀번호), Authority(권한) 등의 정보
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(email, password);
            log.info(String.valueOf(authToken.toString()));
            return this.getAuthenticationManager().authenticate(authToken);
        } catch (AuthenticationServiceException e) {
            log.info("로그인에 실패했습니다. 원인: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 로그인 성공 시 실행하는 메소드 (여기서 JWT, 발급하면 됨)
     * @param request
     * @param response
     * @param chain
     * @param authentication the object returned from the <tt>attemptAuthentication</tt>
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication) throws IOException, ServletException
    {
        // 개발 단계에서 로그확인. 배포 후 : 없앨 예정
        log.info("로그인에 성공했습니다.");
        log.info("유저 메일: " + authentication.getName());
        log.info("유저 권한: " + authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));

        String email = authentication.getName();
        Member memberByEmail = memberService.findMemberByEmail(email);

        if (!memberByEmail.isCertifiedByEmail()) {
            throw new AuthenticationServiceException("Email is not certified yet.");
        }

        String memberId = memberByEmail.getId().toString(); // 토큰에 넣을때, 문자열로 넣습니다.

        // 권한을 문자열로 변환
        String role = extractAuthority(authentication);

        // 토큰 종류(카테고리), 유저이름, 역할 등을 페이로드에 담는다.
        String newAccess = jwtUtil.createAccessToken("access", memberId, role);
        String newRefresh = jwtUtil.createRefreshToken("refresh", memberId, role);

        // [Refresh 토큰 - DB 에서 관리합니다.] 리프레쉬 토큰 권리권한이 서버에 있다.
        saveOrUpdateRefreshEntity(memberByEmail, newRefresh);

        // [response.data] 에 Json 형태로 accessToken 과 refreshToken 을 넣어주는 방식
        addResponseDataV3(response, newAccess, newRefresh, email);
    }

    /**
     * 로그인 실패 시 401 응답 반환
     * @param request
     * @param response
     * @param failed
     * @throws IOException
     * @throws ServletException
     */
    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException, ServletException {

        log.info("로그인에 실패했습니다. ");
        //로그인 실패시 401 응답 코드 반환
        response.setStatus(401);
        response.getWriter().write("로그인에 실패했습니다! ");
        super.unsuccessfulAuthentication(request, response, failed);
    }

    // 사용자의 권한 정보를 가져옴
    private String extractAuthority(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("USER"); // 기본 권한 설정. [따로 설정하지 않았을때]
    }

    /**
     * 쿠키에 refreshToken 을 넣어주는 방식
     */
    private void addResponseDataV3(HttpServletResponse response, String accessToken, String refreshToken, String email) throws IOException {
        // 액세스 토큰을 JsonObject 형식으로 응답 데이터에 포함하여 클라이언트에게 반환
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // JSON 객체를 생성하고 액세스 토큰을 추가
        JsonObject responseData = new JsonObject();
        responseData.addProperty("accessToken", accessToken);
        responseData.addProperty("refreshToken", refreshToken);
        response.getWriter().write(responseData.toString());
        // 리프레시 토큰을 쿠키에 저장 (로컬용)
        CookieUtil.createCookieWithSameSiteForLocal(response, "refreshAuthorization", "Bearer+" + refreshToken, 1209600);
        // HttpStatus 200 OK
        response.setStatus(HttpStatus.OK.value());
    }

    /**
     * [RefreshToken - DB 관리] 리프레쉬 토큰 관리권한이 서버에 있다.
     * 로그인에 성공 시, 이미 가지고 있던 리프레쉬 토큰 or 처믕 로그인한 유저에 대해 리프레쉬 토큰을 DB에 업데이트.
     * @param member 회원의 PK로, member의 refresh Token를 조회.
     * @param newRefreshToken
     */
    private void saveOrUpdateRefreshEntity(Member member, String newRefreshToken) {
        // memberId 로 refresh 엔티티 조회 (중복 저장 방지)
        Optional<RefreshToken> existedRefresh = refreshTokenRepository.findByMember(member);
        LocalDateTime expirationDateTime = LocalDateTime.now().plusSeconds(refreshTokenExpirationPeriod);

        if (existedRefresh.isPresent()) {
            // 이미 존재한다면, 기존 엔티티 업데이트
            RefreshToken refreshTokenEntity = existedRefresh.get();
            RefreshRequest refreshRequest = RefreshRequest.createRefreshDto(newRefreshToken, expirationDateTime);
            refreshTokenEntity.updateRefreshToken(refreshRequest);
            refreshTokenRepository.save(refreshTokenEntity);
        } else {
            // 없으면 새로 저장
            RefreshToken newRefreshTokenEntity = new RefreshToken(member, newRefreshToken, expirationDateTime);
            refreshTokenRepository.save(newRefreshTokenEntity);
        }
    }
}






















