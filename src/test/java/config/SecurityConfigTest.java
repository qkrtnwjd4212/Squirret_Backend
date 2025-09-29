package config;

import com.squirret.squirretbackend.security.CustomOAuth2UserService;
import com.squirret.squirretbackend.security.JwtAuthenticationFilter;
import com.squirret.squirretbackend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {
    
    @Mock
    private JwtService jwtService;
    
    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Mock
    private CustomOAuth2UserService customOAuth2UserService;
    
    @InjectMocks
    private SecurityConfig securityConfig;
    
    @Test
    void testOidcUserService() {
        // When
        OidcUserService result = securityConfig.oidcUserService();
        
        // Then
        assertNotNull(result);
        assertTrue(result instanceof OidcUserService);
    }
    
    @Test
    void testAuthenticationSuccessHandler() {
        // When
        AuthenticationSuccessHandler result = securityConfig.authenticationSuccessHandler();
        
        // Then
        assertNotNull(result);
    }
    
    @Test
    void testCorsConfigurationSource() {
        // When
        CorsConfigurationSource result = securityConfig.corsConfigurationSource();
        
        // Then
        assertNotNull(result);
    }
}
