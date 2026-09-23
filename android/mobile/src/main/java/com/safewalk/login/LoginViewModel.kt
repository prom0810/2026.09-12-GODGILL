package com.safewalk.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safewalk.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepository = AuthRepository(application)

    var uiState by mutableStateOf(LoginUiState())
        private set

    fun updateUserId(value: String) {
        uiState = uiState.copy(userId = value, errorMessage = null)
    }

    fun updatePassword(value: String) {
        uiState = uiState.copy(password = value, errorMessage = null)
    }

    fun selectUserType(value: UserType) {
        uiState = uiState.copy(userType = value)
    }

    /**
     * 로그인 API 호출. 이메일/비밀번호 형식 검증은 하지 않으며(빈 값 여부는 canLogin에서 확인),
     * 실제 일치 여부 확인은 백엔드가 담당한다. 성공 시 토큰이 저장되고 onSuccess가 호출된다.
     */
    fun login(onSuccess: () -> Unit) {
        if (!uiState.canLogin) return
        val request = uiState.toLoginRequest()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    authRepository.login(email = request.userId, password = request.password)
                }
                uiState = uiState.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, errorMessage = e.message ?: "로그인에 실패했습니다.")
            }
        }
    }
}
