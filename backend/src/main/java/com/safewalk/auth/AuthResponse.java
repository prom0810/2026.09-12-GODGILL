package com.safewalk.auth;

/**
 * 회원가입/로그인 공통 응답. Android는 accessToken을 저장해 이후 요청에 사용한다.
 */
public record AuthResponse(Long userId, String email, String userType, String accessToken) {
}
