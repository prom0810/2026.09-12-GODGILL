package com.safewalk.auth;

/**
 * 로그인 요청.
 * 요청 JSON에서 필드가 빠지면 null 대신 빈 문자열로 채운다(기존 Kotlin 기본값과 동일한 동작).
 */
public record LoginRequest(String email, String password) {

    public LoginRequest {
        email = email == null ? "" : email;
        password = password == null ? "" : password;
    }
}
