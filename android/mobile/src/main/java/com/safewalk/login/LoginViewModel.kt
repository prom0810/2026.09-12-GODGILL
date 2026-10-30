package com.safewalk.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safewalk.auth.AuthRepository
import com.safewalk.auth.GuardianSignupApiRequest
import com.safewalk.notification.FcmTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(application)

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun updateLoginEmail(value: String) = update { copy(loginEmail = value, errorMessage = null) }
    fun updateLoginPassword(value: String) = update { copy(loginPassword = value, errorMessage = null) }
    fun updateName(value: String) = update { copy(name = value) }
    fun updateSignUpEmail(value: String) = update { copy(signUpEmail = value) }
    fun updateSignUpPassword(value: String) = update { copy(signUpPassword = value) }
    fun updatePhoneNumber(value: String) = update { copy(phoneNumber = value) }
    fun updateGuardianName(value: String) = update { copy(guardianName = value, errorMessage = null) }
    fun updateGuardianPhone(value: String) = update { copy(guardianPhone = value, errorMessage = null) }
    fun setTermsAgreement(value: Boolean) = update { copy(agreedToTerms = value) }
    fun setPrivacyAgreement(value: Boolean) = update { copy(agreedToPrivacy = value) }
    fun setLocationAgreement(value: Boolean) = update { copy(agreedToLocation = value) }
    fun setAllAgreements(value: Boolean) = update {
        copy(
            agreedToTerms = value,
            agreedToPrivacy = value,
            agreedToLocation = value,
        )
    }
    fun selectUserType(value: UserType) = update { copy(userType = value) }
    fun setNotificationPermission(value: Boolean) = update { copy(notificationPermission = value) }
    fun setActivityPermission(value: Boolean) = update { copy(activityPermission = value) }
    fun setContactsPermission(value: Boolean) = update { copy(contactsPermission = value) }
    fun openSignUp() = update { copy(step = AuthStep.SIGN_UP) }
    fun continueToTerms() = update { copy(step = AuthStep.TERMS) }
    fun continueToUserType() = update { copy(step = AuthStep.USER_TYPE) }
    fun continueToPermissions() = update { copy(step = AuthStep.PERMISSIONS) }

    fun login(onSuccess: () -> Unit) {
        if (!uiState.canLogin) return
        val request = uiState.toLoginRequest()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    authRepository.login(email = request.email, password = request.password)
                }
                uiState = uiState.copy(isLoading = false)
                FcmTokenManager.sync(getApplication())
                onSuccess()
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "로그인에 실패했습니다.",
                )
            }
        }
    }

    fun signup(onSuccess: () -> Unit) {
        if (!uiState.canContinueSignUp || !uiState.canContinueUserType || uiState.isLoading) return
        val request = uiState.toSignUpRequest()
        val backendType = if (request.userType == UserType.CHILD) "MINOR" else "ADULT"
        val guardians = if (request.userType == UserType.CHILD) {
            listOf(GuardianSignupApiRequest(request.guardianName, request.guardianPhone))
        } else {
            emptyList()
        }
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    authRepository.signup(
                        email = request.email,
                        password = request.password,
                        name = request.name,
                        phone = request.phoneNumber,
                        userType = backendType,
                        guardians = guardians,
                    )
                }
                uiState = uiState.copy(isLoading = false)
                FcmTokenManager.sync(getApplication())
                onSuccess()
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "회원가입에 실패했습니다.",
                )
            }
        }
    }

    fun goBack() = update {
        copy(step = when (step) {
            AuthStep.LOGIN -> AuthStep.LOGIN
            AuthStep.SIGN_UP -> AuthStep.LOGIN
            AuthStep.TERMS -> AuthStep.SIGN_UP
            AuthStep.USER_TYPE -> AuthStep.TERMS
            AuthStep.PERMISSIONS -> AuthStep.USER_TYPE
        })
    }

    private inline fun update(block: LoginUiState.() -> LoginUiState) {
        uiState = uiState.block()
    }
}
