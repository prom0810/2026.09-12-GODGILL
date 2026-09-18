package com.safewalk.login

enum class UserType {
    ADULT,
    MINOR,
}

data class LoginUiState(
    val userId: String = "",
    val password: String = "",
    val userType: UserType = UserType.ADULT,
) {
    val canLogin: Boolean
        get() = userId.isNotBlank() && password.isNotBlank()

    fun toLoginRequest() = LoginRequest(
        userId = userId.trim(),
        password = password,
        userType = userType,
    )
}

/** Spring Boot 로그인 API 연결 시 요청 DTO로 변환할 화면 독립 모델입니다. */
data class LoginRequest(
    val userId: String,
    val password: String,
    val userType: UserType,
)
