package com.safewalk.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    fun updateLoginEmail(value: String) = update { copy(loginEmail = value) }
    fun updateLoginPassword(value: String) = update { copy(loginPassword = value) }
    fun updateName(value: String) = update { copy(name = value) }
    fun updateSignUpEmail(value: String) = update { copy(signUpEmail = value) }
    fun updateSignUpPassword(value: String) = update { copy(signUpPassword = value) }
    fun updatePhoneNumber(value: String) = update { copy(phoneNumber = value) }
    fun setTermsAgreement(value: Boolean) = update { copy(agreedToTerms = value) }
    fun selectUserType(value: UserType) = update { copy(userType = value) }
    fun setNotificationPermission(value: Boolean) = update { copy(notificationPermission = value) }
    fun setActivityPermission(value: Boolean) = update { copy(activityPermission = value) }
    fun setContactsPermission(value: Boolean) = update { copy(contactsPermission = value) }
    fun openSignUp() = update { copy(step = AuthStep.SIGN_UP) }
    fun continueToUserType() = update { copy(step = AuthStep.USER_TYPE) }
    fun continueToPermissions() = update { copy(step = AuthStep.PERMISSIONS) }

    fun goBack() = update {
        copy(step = when (step) {
            AuthStep.LOGIN -> AuthStep.LOGIN
            AuthStep.SIGN_UP -> AuthStep.LOGIN
            AuthStep.USER_TYPE -> AuthStep.SIGN_UP
            AuthStep.PERMISSIONS -> AuthStep.USER_TYPE
        })
    }

    private inline fun update(block: LoginUiState.() -> LoginUiState) {
        uiState = uiState.block()
    }
}
