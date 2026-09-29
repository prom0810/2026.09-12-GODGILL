package com.safewalk.login

enum class UserType {
    ADULT,
    MINOR,
}

data class LoginUiState(
    val userId: String = "",
    val password: String = "",
    val userType: UserType = UserType.ADULT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canLogin: Boolean
        get() = userId.isNotBlank() && password.isNotBlank() && !isLoading

    fun toLoginRequest() = LoginRequest(
        userId = userId.trim(),
        password = password,
        userType = userType,
    )
}

/**
 * 화면 독립 로그인 요청 모델. userId는 화면 표시상 "아이디"지만 실제로는 이메일 주소를
 * 그대로 사용한다 (백엔드 로그인 식별자가 email이므로). API 호출 시 이 값을 email로 전달한다.
 */
data class LoginRequest(
    val userId: String,
    val password: String,
    val userType: UserType,
)
