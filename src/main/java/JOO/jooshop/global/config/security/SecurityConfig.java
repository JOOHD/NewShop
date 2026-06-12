package JOO.jooshop.global.config.security;

import JOO.jooshop.global.authentication.factory.FilterFactory;
import JOO.jooshop.global.authentication.jwts.filter.CustomLogoutFilter;
import JOO.jooshop.global.authentication.jwts.filter.JWTFilterV3;
import JOO.jooshop.global.authentication.jwts.handler.FormLoginFailureHandler;
import JOO.jooshop.global.authentication.jwts.handler.FormLoginSuccessHandler;
import JOO.jooshop.global.authentication.jwts.utils.JWTUtil;
import JOO.jooshop.global.authentication.jwts.utils.TokenCookieWriter;
import JOO.jooshop.global.authentication.oauth2.custom.service.CustomOAuth2UserService;
import JOO.jooshop.global.authentication.oauth2.handler.OAuth2LoginFailureHandler;
import JOO.jooshop.global.authentication.oauth2.handler.OAuth2LoginSuccessHandler;
import JOO.jooshop.members.repository.RefreshTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfigurationSource;

import static JOO.jooshop.global.config.security.SecurityPath.*;

/**
 * Spring Security 인증/인가 설정.
 *
 * 로그인 방식:
 * - 일반 로그인: POST /formLogin (Form Login) → FormLoginSuccessHandler → JWT 쿠키 발급
 * - 소셜 로그인: OAuth2 (카카오, 네이버) → OAuth2LoginSuccessHandler → JWT 쿠키 발급
 *
 * API 요청: JWT 쿠키 기반 인증 (JWTFilterV3)
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTUtil jwtUtil;
    private final FilterFactory filterFactory;
    private final RedisTemplate<String, String> redisTemplate;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final FormLoginSuccessHandler formLoginSuccessHandler;
    private final FormLoginFailureHandler formLoginFailureHandler;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oauth2LoginFailureHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    /**
     * API 요청용 SecurityFilterChain.
     * JWT 기반 Stateless 인증. /api/** 요청만 처리.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {

        JWTFilterV3 jwtFilter = filterFactory.createJWTFilter();

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

        return http.build();
    }

    /**
     * Web 요청용 SecurityFilterChain.
     * Form Login + OAuth2 Login. 브라우저 화면 요청 처리.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            RefreshTokenRepository refreshTokenRepository,
            ObjectMapper objectMapper,
            TokenCookieWriter tokenCookieWriter
    ) throws Exception {

        JWTFilterV3 jwtFilter = filterFactory.createJWTFilter();
        CustomLogoutFilter customLogoutFilter =
                new CustomLogoutFilter(jwtUtil, redisTemplate, refreshTokenRepository, objectMapper, tokenCookieWriter);

        http
                .securityMatcher("/**")
                .csrf(csrf -> csrf
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
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService))
                        .successHandler(oauth2LoginSuccessHandler)
                        .failureHandler(oauth2LoginFailureHandler)
                )
                // Spring 내장 LogoutFilter 비활성화 — CustomLogoutFilter가 POST /logout을 단독 처리.
                // CustomLogoutFilter: Redis blacklist + RefreshToken 삭제 + 쿠키 초기화 + 세션 무효화
                .logout(AbstractHttpConfigurer::disable);

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(customLogoutFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
