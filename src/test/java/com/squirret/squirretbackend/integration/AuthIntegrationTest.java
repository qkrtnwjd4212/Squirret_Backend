package com.squirret.squirretbackend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squirret.squirretbackend.dto.AuthResponseDto;
import com.squirret.squirretbackend.entity.User;
import com.squirret.squirretbackend.repository.UserRepository;
import com.squirret.squirretbackend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtService jwtService;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testRefreshTokenFlow() throws Exception {
        // Given
        User testUser = User.builder()
                .email("test@example.com")
                .name("Test User")
                .provider(User.Provider.KAKAO)
                .providerId("123456789")
                .build();
        userRepository.save(testUser);
        
        String refreshToken = jwtService.generateRefreshToken(testUser.getId(), testUser.getEmail());
        Map<String, String> request = Map.of("refreshToken", refreshToken);
        
        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("토큰이 성공적으로 갱신되었습니다."))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }
    
    @Test
    void testRefreshTokenWithInvalidToken() throws Exception {
        // Given
        Map<String, String> request = Map.of("refreshToken", "invalid-token");
        
        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("토큰 처리 중 오류가 발생했습니다."));
    }
    
    @Test
    void testRefreshTokenWithMissingToken() throws Exception {
        // Given
        Map<String, String> request = Map.of("refreshToken", "");
        
        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("리프레시 토큰이 필요합니다."));
    }
    
    @Test
    void testLogout() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."));
    }
    
    @Test
    void testGetCurrentUserWithoutAuthentication() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("인증되지 않은 사용자입니다."));
    }
    
    @Test
    void testOAuth2LoginEndpoints() throws Exception {
        // 카카오 로그인 엔드포인트 테스트
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().is3xxRedirection());
        
        // 구글 로그인 엔드포인트 테스트
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
        
        // 애플 로그인 엔드포인트 테스트
        mockMvc.perform(get("/oauth2/authorization/apple"))
                .andExpect(status().is3xxRedirection());
    }
}
