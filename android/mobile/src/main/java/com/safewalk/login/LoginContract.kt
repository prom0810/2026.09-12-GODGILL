package com.safewalk.login

enum class AuthStep { LOGIN, SIGN_UP, TERMS, USER_TYPE, PERMISSIONS }

enum class UserType { CHILD, GUARDIAN, COMPANION, ADULT, MINOR }

data class LoginUiState(
    val step: AuthStep = AuthStep.LOGIN,
    val loginEmail: String = "",
    val loginPassword: String = "",
    val name: String = "",
    val signUpEmail: String = "",
    val signUpPassword: String = "",
    val phoneNumber: String = "",
    val agreedToTerms: Boolean = false,
    val agreedToPrivacy: Boolean = false,
    val agreedToLocation: Boolean = false,
    val userType: UserType? = null,
    val notificationPermission: Boolean = true,
    val activityPermission: Boolean = true,
    val contactsPermission: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val canLogin: Boolean
        get() = loginEmail.isNotBlank() && loginPassword.isNotBlank() && !isLoading

    val canContinueSignUp: Boolean
        get() = name.isNotBlank() &&
            signUpEmail.isNotBlank() &&
            signUpPassword.isNotBlank() &&
            phoneNumber.isNotBlank()

    val canContinueTerms: Boolean
        get() = agreedToTerms && agreedToPrivacy

    val allAgreementsSelected: Boolean
        get() = agreedToTerms && agreedToPrivacy && agreedToLocation

    val canContinueUserType: Boolean
        get() = userType != null

    fun toLoginRequest() = LoginRequest(loginEmail.trim(), loginPassword)

    fun toSignUpRequest() = SignUpRequest(
        name.trim(), signUpEmail.trim(), signUpPassword, phoneNumber.trim(), requireNotNull(userType),
    )
}

/** 백엔드 연결 전까지 화면 입력값을 담아 두는 요청 모델입니다. */
data class LoginRequest(val email: String, val password: String)

data class SignUpRequest(
    val name: String,
    val email: String,
    val password: String,
    val phoneNumber: String,
    val userType: UserType,
)
