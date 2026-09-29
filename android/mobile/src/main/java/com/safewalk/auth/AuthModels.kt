package com.safewalk.auth

/** 백엔드 SignupRequest와 필드를 맞춘 네트워크 요청 모델 (com.safewalk.auth.SignupRequest, 백엔드). */
data class SignupApiRequest(
    val email: String,
    val password: String,
    val userType: String,
)

/** 백엔드 LoginRequest와 필드를 맞춘 네트워크 요청 모델. */
data class LoginApiRequest(
    val email: String,
    val password: String,
)

/** 백엔드 AuthResponse와 필드를 맞춘 응답 모델. */
data class AuthApiResult(
    val userId: Long,
    val email: String,
    val userType: String,
    val accessToken: String,
)
