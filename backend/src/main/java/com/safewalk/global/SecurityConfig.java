package com.safewalk.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 등 공통 보안 관련 빈.
 * 요청 인증(JWT 검사)은 Spring Security 필터 체인 대신 {@link AuthInterceptor} + {@link WebConfig}로 처리한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
