package com.safewalk.signup

import com.safewalk.login.UserType

/**
 * 회원가입 화면 상태.
 * 현 단계에서는 이메일·비밀번호·사용자 유형만 받는다 — 이메일 형식, 비밀번호 길이 같은
 * 세부 검증은 하지 않고 빈 값 여부만 확인한다(canSubmit). 이메일 중복 여부는 백엔드가 확인한다.
 */
data class SignupUiState(
    val email: String = "",
    val password: String = "",
    val userType: UserType = UserType.ADULT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && !isLoading
}
