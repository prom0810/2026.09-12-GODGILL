package com.safewalk.signup

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safewalk.auth.AuthRepository
import com.safewalk.login.UserType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignupViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(application)

    var uiState by mutableStateOf(SignupUiState())
        private set

    fun updateEmail(value: String) {
        uiState = uiState.copy(email = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        uiState = uiState.copy(password = value, errorMessage = null)
    }

    fun selectUserType(value: UserType) {
        uiState = uiState.copy(userType = value)
    }

    /**
     * 회원가입 API 호출. 성공하면 로그인과 마찬가지로 토큰이 저장되고 onSuccess가 호출된다
     * (회원가입 직후 바로 지도 화면으로 진입 — 별도 로그인 단계를 다시 거치지 않는다).
     */
    fun signup(onSuccess: () -> Unit) {
        if (!uiState.canSubmit) return
        val email = uiState.email.trim()
        val password = uiState.password
        val userType = uiState.userType
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    authRepository.signup(email = email, password = password, userType = userType.name)
                }
                uiState = uiState.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, errorMessage = e.message ?: "회원가입에 실패했습니다.")
            }
        }
    }
}
