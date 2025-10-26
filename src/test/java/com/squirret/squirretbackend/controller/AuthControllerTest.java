package com.squirret.squirretbackend.controller;

import com.squirret.squirretbackend.dto.AuthResponseDto;
import com.squirret.squirretbackend.entity.User;
import com.squirret.squirretbackend.service.JwtService;
import com.squirret.squirretbackend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    
    @Mock
    private JwtService jwtService;
    
    @Mock
    private UserService userService;
    
    @Mock
    private SecurityContext securityContext;
    
    @Mock
    private Authentication authentication;
    
    @Mock
    private OAuth2User oAuth2User;
    
    @InjectMocks
    private AuthController authController;
    
    private User mockUser;
    
    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .name("Test User")
                .profileImageUrl("https://example.com/profile.jpg")
                .provider(User.Provider.KAKAO)
                .providerId("123456789")
                .build();
    }
    
    @Test
    void testGetCurrentUser_Success() {
        // Given
        when(oAuth2User.getAttribute("email")).thenReturn("test@example.com");
        when(userService.findByEmail("test@example.com")).thenReturn(Optional.of(mockUser));
        
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        
        // When
        ResponseEntity<Map<String, Object>> response = authController.getCurrentUser(oAuth2User);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals(mockUser.getId(), body.get("id"));
        assertEquals(mockUser.getEmail(), body.get("email"));
        assertEquals(mockUser.getName(), body.get("name"));
        assertEquals(mockUser.getProfileImageUrl(), body.get("profileImageUrl"));
        assertEquals(mockUser.getProvider(), body.get("provider"));
    }
    
    @Test
    void testGetCurrentUser_Unauthorized() {
        // Given
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(null);
        
        // When
        ResponseEntity<Map<String, Object>> response = authController.getCurrentUser(null);
        
        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("인증되지 않은 사용자입니다.", response.getBody().get("error"));
    }
    
    @Test
    void testGetCurrentUser_UserNotFound() {
        // Given
        when(oAuth2User.getAttribute("email")).thenReturn("notfound@example.com");
        when(userService.findByEmail("notfound@example.com")).thenReturn(Optional.empty());
        
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        
        // When
        ResponseEntity<Map<String, Object>> response = authController.getCurrentUser(oAuth2User);
        
        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("사용자를 찾을 수 없습니다.", response.getBody().get("error"));
    }
    
    @Test
    void testRefreshToken_Success() {
        // Given
        String refreshToken = "valid-refresh-token";
        String email = "test@example.com";
        UUID userId = UUID.randomUUID();
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";
        
        Map<String, String> request = Map.of("refreshToken", refreshToken);
        
        when(jwtService.extractUsername(refreshToken)).thenReturn(email);
        when(jwtService.extractUserId(refreshToken)).thenReturn(userId);
        when(jwtService.isTokenValid(refreshToken, email)).thenReturn(true);
        when(jwtService.generateToken(userId, email)).thenReturn(newAccessToken);
        when(jwtService.generateRefreshToken(userId, email)).thenReturn(newRefreshToken);
        
        // When
        ResponseEntity<AuthResponseDto> response = authController.refreshToken(request);
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        AuthResponseDto body = response.getBody();
        assertEquals("토큰이 성공적으로 갱신되었습니다.", body.getMessage());
        assertEquals(newAccessToken, body.getAccessToken());
        assertEquals(newRefreshToken, body.getRefreshToken());
    }
    
    @Test
    void testRefreshToken_InvalidToken() {
        // Given
        String refreshToken = "invalid-refresh-token";
        String email = "test@example.com";
        
        Map<String, String> request = Map.of("refreshToken", refreshToken);
        
        when(jwtService.extractUsername(refreshToken)).thenReturn(email);
        when(jwtService.isTokenValid(refreshToken, email)).thenReturn(false);
        
        // When
        ResponseEntity<AuthResponseDto> response = authController.refreshToken(request);
        
        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("유효하지 않은 리프레시 토큰입니다.", response.getBody().getMessage());
    }
    
    @Test
    void testRefreshToken_MissingToken() {
        // Given
        Map<String, String> request = Map.of("refreshToken", "");
        
        // When
        ResponseEntity<AuthResponseDto> response = authController.refreshToken(request);
        
        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("리프레시 토큰이 필요합니다.", response.getBody().getMessage());
    }
    
    @Test
    void testRefreshToken_Exception() {
        // Given
        String refreshToken = "malformed-token";
        Map<String, String> request = Map.of("refreshToken", refreshToken);
        
        when(jwtService.extractUsername(refreshToken)).thenThrow(new RuntimeException("Token parsing error"));
        
        // When
        ResponseEntity<AuthResponseDto> response = authController.refreshToken(request);
        
        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("토큰 처리 중 오류가 발생했습니다.", response.getBody().getMessage());
    }
    
    @Test
    void testLogout() {
        // When
        ResponseEntity<Map<String, String>> response = authController.logout();
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("로그아웃되었습니다.", response.getBody().get("message"));
    }
}
