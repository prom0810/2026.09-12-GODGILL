package com.safewalk.global;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 등 공통 보안 관련 빈.
 * 아직 Spring Security 필터 체인(요청 인증/인가)은 구성하지 않았다 — 다른 API를
 * JWT로 보호할 필요가 생기면 이곳에 SecurityFilterChain을 추가한다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
