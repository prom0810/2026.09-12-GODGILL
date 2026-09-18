package com.safewalk.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {
    var uiState by mutableStateOf(LoginUiState())
        private set

    fun updateUserId(value: String) {
        uiState = uiState.copy(userId = value)
    }

    fun updatePassword(value: String) {
        uiState = uiState.copy(password = value)
    }

    fun selectUserType(value: UserType) {
        uiState = uiState.copy(userType = value)
    }
}
