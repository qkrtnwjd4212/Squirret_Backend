package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.entity.User;
import com.squirret.squirretbackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    private OAuth2User mockOAuth2User;
    private User mockUser;
    
    @BeforeEach
    void setUp() {
        mockOAuth2User = mock(OAuth2User.class);
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
    void testCreateOrUpdateUser_NewUser() {
        // Given
        when(mockOAuth2User.getAttribute("email")).thenReturn("test@example.com");
        when(mockOAuth2User.getAttribute("name")).thenReturn("Test User");
        when(mockOAuth2User.getAttribute("picture")).thenReturn("https://example.com/profile.jpg");
        when(mockOAuth2User.getAttribute("id")).thenReturn("123456789");
        
        when(userRepository.findByProviderAndProviderId(User.Provider.KAKAO, "123456789"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        
        // When
        User result = userService.createOrUpdateUser(mockOAuth2User, User.Provider.KAKAO);
        
        // Then
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test User", result.getName());
        assertEquals(User.Provider.KAKAO, result.getProvider());
        assertEquals("123456789", result.getProviderId());
        
        verify(userRepository).findByProviderAndProviderId(User.Provider.KAKAO, "123456789");
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void testCreateOrUpdateUser_ExistingUser() {
        // Given
        when(mockOAuth2User.getAttribute("email")).thenReturn("test@example.com");
        when(mockOAuth2User.getAttribute("name")).thenReturn("Updated Name");
        when(mockOAuth2User.getAttribute("picture")).thenReturn("https://example.com/new-profile.jpg");
        when(mockOAuth2User.getAttribute("id")).thenReturn("123456789");
        
        when(userRepository.findByProviderAndProviderId(User.Provider.KAKAO, "123456789"))
                .thenReturn(Optional.of(mockUser));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        
        // When
        User result = userService.createOrUpdateUser(mockOAuth2User, User.Provider.KAKAO);
        
        // Then
        assertNotNull(result);
        assertEquals("Updated Name", result.getName());
        assertEquals("https://example.com/new-profile.jpg", result.getProfileImageUrl());
        
        verify(userRepository).findByProviderAndProviderId(User.Provider.KAKAO, "123456789");
        verify(userRepository).save(any(User.class));
    }
    
    @Test
    void testCreateOrUpdateUser_GoogleProvider() {
        // Given
        when(mockOAuth2User.getAttribute("email")).thenReturn("test@gmail.com");
        when(mockOAuth2User.getAttribute("name")).thenReturn("Google User");
        when(mockOAuth2User.getAttribute("picture")).thenReturn("https://google.com/profile.jpg");
        when(mockOAuth2User.getAttribute("sub")).thenReturn("google123456");
        
        when(userRepository.findByProviderAndProviderId(User.Provider.KAKAO, "google123456"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        
        // When
        User result = userService.createOrUpdateUser(mockOAuth2User, User.Provider.KAKAO);
        
        // Then
        assertNotNull(result);
        assertEquals(User.Provider.KAKAO, result.getProvider());
        
        verify(userRepository).findByProviderAndProviderId(User.Provider.KAKAO, "google123456");
    }
    
    @Test
    void testCreateOrUpdateUser_AppleProvider() {
        // Given
        when(mockOAuth2User.getAttribute("email")).thenReturn("test@icloud.com");
        when(mockOAuth2User.getAttribute("name")).thenReturn("Apple User");
        when(mockOAuth2User.getAttribute("picture")).thenReturn("https://apple.com/profile.jpg");
        when(mockOAuth2User.getAttribute("sub")).thenReturn("apple123456");
        
        when(userRepository.findByProviderAndProviderId(User.Provider.APPLE, "apple123456"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        
        // When
        User result = userService.createOrUpdateUser(mockOAuth2User, User.Provider.APPLE);
        
        // Then
        assertNotNull(result);
        assertEquals(User.Provider.APPLE, result.getProvider());
        
        verify(userRepository).findByProviderAndProviderId(User.Provider.APPLE, "apple123456");
    }
    
    @Test
    void testFindByEmail() {
        // Given
        String email = "test@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));
        
        // When
        Optional<User> result = userService.findByEmail(email);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(email, result.get().getEmail());
        
        verify(userRepository).findByEmail(email);
    }
    
    @Test
    void testFindByEmail_NotFound() {
        // Given
        String email = "notfound@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());
        
        // When
        Optional<User> result = userService.findByEmail(email);
        
        // Then
        assertFalse(result.isPresent());
        
        verify(userRepository).findByEmail(email);
    }
    
    @Test
    void testFindById() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        
        // When
        Optional<User> result = userService.findById(userId);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().getId());
        
        verify(userRepository).findById(userId);
    }
}
