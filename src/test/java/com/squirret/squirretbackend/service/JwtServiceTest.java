package com.squirret.squirretbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {
    
    private JwtService jwtService;
    
    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // 테스트용 시크릿 키 설정
        ReflectionTestUtils.setField(jwtService, "secretKey", "mySecretKey123456789012345678901234567890");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L); // 24시간
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L); // 7일
    }
    
    @Test
    void testGenerateToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        
        // When
        String token = jwtService.generateToken(userId, email);
        
        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }
    
    @Test
    void testExtractUsername() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtService.generateToken(userId, email);
        
        // When
        String extractedEmail = jwtService.extractUsername(token);
        
        // Then
        assertEquals(email, extractedEmail);
    }
    
    @Test
    void testExtractUserId() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtService.generateToken(userId, email);
        
        // When
        UUID extractedUserId = jwtService.extractUserId(token);
        
        // Then
        assertEquals(userId, extractedUserId);
    }
    
    @Test
    void testIsTokenValid() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String token = jwtService.generateToken(userId, email);
        
        // When
        boolean isValid = jwtService.isTokenValid(token, email);
        
        // Then
        assertTrue(isValid);
    }
    
    @Test
    void testIsTokenValidWithWrongEmail() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String wrongEmail = "wrong@example.com";
        String token = jwtService.generateToken(userId, email);
        
        // When
        boolean isValid = jwtService.isTokenValid(token, wrongEmail);
        
        // Then
        assertFalse(isValid);
    }
    
    @Test
    void testGenerateRefreshToken() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        
        // When
        String refreshToken = jwtService.generateRefreshToken(userId, email);
        
        // Then
        assertNotNull(refreshToken);
        assertFalse(refreshToken.isEmpty());
        
        // 리프레시 토큰도 유효한지 확인
        assertTrue(jwtService.isTokenValid(refreshToken, email));
    }
    
    @Test
    void testTokenWithExtraClaims() {
        // Given
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        java.util.Map<String, Object> extraClaims = java.util.Map.of("role", "USER");
        
        // When
        String token = jwtService.generateToken(extraClaims, userId, email);
        
        // Then
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token, email));
    }
}
