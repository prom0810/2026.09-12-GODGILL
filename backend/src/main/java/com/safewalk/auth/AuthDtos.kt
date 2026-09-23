package com.safewalk.auth

/**
 * 회원가입 요청. 현재는 이메일·비밀번호·사용자 유형(ADULT/MINOR)만 받는다.
 * 이름·전화번호는 아직 화면에 없으므로 요청 모델에도 포함하지 않는다.
 */
data class SignupRequest(
    val email: String = "",
    val password: String = "",
    val userType: String = ""
)

data class LoginRequest(
    val email: String = "",
    val password: String = ""
)

/**
 * 회원가입/로그인 공통 응답. Android는 accessToken을 저장해 이후 요청에 사용한다.
 */
data class AuthResponse(
    val userId: Long,
    val email: String,
    val userType: String,
    val accessToken: String
)
