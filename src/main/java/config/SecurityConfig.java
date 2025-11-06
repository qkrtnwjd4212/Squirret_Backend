package config;

import com.squirret.squirretbackend.security.CustomOAuth2UserService;
import com.squirret.squirretbackend.security.JwtAuthenticationFilter;
import com.squirret.squirretbackend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtService jwtService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // FSR 데이터 API는 가장 먼저 명시적으로 permitAll 설정 (우선순위 높게)
                .requestMatchers("/api/fsr_data/**").permitAll()
                .requestMatchers("/api/auth/**", "/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/api/fitness/**").authenticated()
                .anyRequest().authenticated()
            )
            // FSR 데이터 API는 인증 실패 시 리다이렉트하지 않도록 설정
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    String path = request.getRequestURI();
                    // FSR 데이터 API는 permitAll이므로 인증 예외가 발생하면 안 되지만, 
                    // 만약 발생한다면 리다이렉트 대신 401 반환
                    if (path.startsWith("/api/fsr_data/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    } else {
                        // 다른 경로는 기본 동작 (로그인 페이지로 리다이렉트)
                        response.sendRedirect("/login");
                    }
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/")
                .defaultSuccessUrl("/", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(oidcUserService())
                    .userService(customOAuth2UserService))
                .successHandler(authenticationSuccessHandler())
            );
            
        return http.build();
    }
    
    @Bean
    public OidcUserService oidcUserService() {
        return new OidcUserService();
    }
    
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            
            // JWT 토큰 생성
            String userId = oauth2User.getAttribute("id");
            String email = oauth2User.getAttribute("email");
            String accessToken = jwtService.generateToken(UUID.fromString(userId), email);
            String refreshToken = jwtService.generateRefreshToken(UUID.fromString(userId), email);
            
            // 클라이언트 타입에 따라 다른 리다이렉트
            // 네이티브 앱: squirret://auth/callback?token=...
            // 웹: http://localhost:3000/auth/callback?token=...
            String redirectUrl = request.getParameter("redirect_uri");
            if (redirectUrl == null || redirectUrl.isEmpty()) {
                redirectUrl = "squirret://auth/callback"; // 기본값: 모바일 앱
            }
            
            response.sendRedirect(redirectUrl + 
                "?access_token=" + accessToken + 
                "&refresh_token=" + refreshToken);
        };
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
