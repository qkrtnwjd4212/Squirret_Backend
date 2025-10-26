package com.squirret.squirretbackend.service;

import com.squirret.squirretbackend.entity.User;
import com.squirret.squirretbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    
    @Transactional
    public User createOrUpdateUser(OAuth2User oauth2User, User.Provider provider) {
        String providerId = getProviderId(oauth2User, provider);
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String profileImageUrl = oauth2User.getAttribute("picture");
        
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, providerId);
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setName(name);
            user.setProfileImageUrl(profileImageUrl);
            return userRepository.save(user);
        } else {
            User newUser = User.builder()
                    .email(email)
                    .name(name)
                    .profileImageUrl(profileImageUrl)
                    .provider(provider)
                    .providerId(providerId)
                    .build();
            return userRepository.save(newUser);
        }
    }
    
    private String getProviderId(OAuth2User oauth2User, User.Provider provider) {
        return switch (provider) {
            case KAKAO -> oauth2User.getAttribute("id") != null ? oauth2User.getAttribute("id").toString() : "";
            case NAVER -> oauth2User.getAttribute("id") != null ? oauth2User.getAttribute("id").toString() : "";
            case APPLE -> oauth2User.getAttribute("sub") != null ? oauth2User.getAttribute("sub") : "";
            case GOOGLE -> oauth2User.getAttribute("id") != null ? oauth2User.getAttribute("id").toString() : "";
        };
    }
    
    @Transactional
    public User upsertOAuthUser(String provider, String providerId, String email, String name) {
        User.Provider providerEnum = User.Provider.valueOf(provider.toUpperCase());
        
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(providerEnum, providerId);
        
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (email != null) user.setEmail(email);
            if (name != null) user.setName(name);
            return userRepository.save(user);
        } else {
            User newUser = User.builder()
                    .email(email)
                    .name(name)
                    .provider(providerEnum)
                    .providerId(providerId)
                    .build();
            return userRepository.save(newUser);
        }
    }
    
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }
}
