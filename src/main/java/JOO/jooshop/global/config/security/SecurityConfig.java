package JOO.jooshop.global.config.security;

import JOO.jooshop.global.authentication.factory.FilterFactory;
import JOO.jooshop.global.authentication.jwts.filter.CustomLogoutFilter;
import JOO.jooshop.global.authentication.jwts.filter.JWTFilterV3;
import JOO.jooshop.global.authentication.jwts.handler.FormLoginFailureHandler;
import JOO.jooshop.global.authentication.jwts.handler.FormLoginSuccessHandler;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.authentication.oauth2.custom.service.CustomOAuth2UserServiceV1;
import JOO.jooshop.global.authentication.oauth2.handler.Oauth2LoginFailureHandler;
import JOO.jooshop.global.authentication.oauth2.handler.Oauth2LoginSuccessHandlerV2;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import JOO.jooshop.members.service.MemberAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import static JOO.jooshop.global.config.security.SecurityPath.*;

/**
 * Spring Security 인증/인가 설정 클래스입니다.
 * 역할:
 * - API 요청은 JWT 기반 Stateless 인증으로 처리합니다.
 * - Web 요청은 Form Login / OAuth2 Login 기반 인증으로 처리합니다.
 * - CORS, CSRF, 세션 정책, 필터 등록 순서를 설정합니다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTUtil jwtUtil;
    private final FilterFactory filterFactory;
    private final RedisTemplate<String, String> redisTemplate;
    private final CustomOAuth2UserServiceV1 customOAuth2UserService;
    private final FormLoginSuccessHandler formLoginSuccessHandler;
    private final FormLoginFailureHandler formLoginFailureHandler;
    private final Oauth2LoginSuccessHandlerV2 oauth2LoginSuccessHandler;
    private final Oauth2LoginFailureHandler oauth2LoginFailureHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * API 요청용 SecurityFilterChain입니다.

     * 특징:
     * - /api/** 요청만 처리합니다.
     * - JWT 기반 인증을 사용합니다.
     * - 세션을 사용하지 않는 Stateless 구조입니다.
     * - CSRF는 비활성화합니다.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            MemberAccountService memberService
    ) throws Exception {

        JWTFilterV3 jwtFilter = filterFactory.createJWTFilter();
        var loginFilter = filterFactory.createLoginFilter(authenticationManager, memberService);

        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_API).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_API).permitAll()

                        .requestMatchers(HttpMethod.POST, USER_OR_SELLER_API).hasAnyRole("USER", "SELLER")
                        .requestMatchers(HttpMethod.PUT, USER_OR_SELLER_API).hasAnyRole("USER", "SELLER")
                        .requestMatchers(HttpMethod.DELETE, USER_OR_SELLER_API).hasAnyRole("USER", "SELLER")

                        .requestMatchers(HttpMethod.POST, ADMIN_API).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, ADMIN_API).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, ADMIN_API).hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Forbidden\"}");
                        })
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(loginFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Web 요청용 SecurityFilterChain입니다.
     *
     * 특징:
     * - 일반 브라우저 화면 요청을 처리합니다.
     * - Form Login과 OAuth2 Login을 사용합니다.
     * - CSRF는 활성화하되, /api/** 요청은 제외합니다.
     * - 세션은 필요한 경우에만 생성합니다.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            RefreshTokenRepository refreshTokenRepository,
            ObjectMapper objectMapper
    ) throws Exception {

        JWTFilterV3 jwtFilter = filterFactory.createJWTFilter();
        CustomLogoutFilter customLogoutFilter =
                new CustomLogoutFilter(jwtUtil, redisTemplate, refreshTokenRepository, objectMapper);

        http
                .securityMatcher("/**")
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/api/**"))
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_WEB).permitAll()
                        .requestMatchers(AUTHENTICATED_WEB).authenticated()
                        .requestMatchers(ADMIN_WEB).hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/formLogin")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(formLoginSuccessHandler)
                        .failureHandler(formLoginFailureHandler)
                        .permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oauth2LoginSuccessHandler)
                        .failureHandler(oauth2LoginFailureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                );

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(customLogoutFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}