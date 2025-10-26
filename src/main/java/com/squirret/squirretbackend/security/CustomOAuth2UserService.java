package com.squirret.squirretbackend.security;

import com.squirret.squirretbackend.entity.User;
import com.squirret.squirretbackend.service.UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;

    public CustomOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        var delegate = new DefaultOAuth2UserService();
        var oauth2User = delegate.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oauth2User.getAttributes();

        String id;
        String email = null;
        String name = null;

        switch (provider) {
            case "naver" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = (Map<String, Object>) attributes.get("response");
                id = (String) response.get("id");
                email = (String) response.get("email");
                name = (String) response.get("name");
            }
            case "kakao" -> {
                // 카카오 v2/user/me API 사용: id/nickname만 수집
                id = (String) attributes.get("id");
                email = null; // 이메일 수집하지 않음
                name = (String) attributes.get("nickname");
            }
            case "google" -> {
                // 구글 OAuth2: id/email/name 수집
                id = (String) attributes.get("id");
                email = (String) attributes.get("email");
                name = (String) attributes.get("name");
            }
            default -> { // apple
                id = (String) attributes.get("sub");
                email = (String) attributes.get("email"); // 최초 동의 때만 있을 수 있음
                name = (String) attributes.get("name");  // 최초 동의 때만 있을 수 있음
            }
        }

        User saved = userService.upsertOAuthUser(provider, id, email, name);

        Map<String, Object> principalAttrs = Map.of(
                "id", saved.getId().toString(),
                "email", saved.getEmail(),
                "name", saved.getName(),
                "provider", saved.getProvider().toString()
        );
        return new DefaultOAuth2User(oauth2User.getAuthorities(), principalAttrs, "id");
    }
}
