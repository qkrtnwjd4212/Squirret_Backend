package com.squirret.squirretbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable()) // csrf 보호 기능 비활성화
                // http 요청에 대한 접근 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/**").permitAll() // api 요청은 모두 인증 없이 허용
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 요구
                );
        return http.build();

    }
}
