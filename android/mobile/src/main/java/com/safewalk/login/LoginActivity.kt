package com.safewalk.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.safewalk.map.MapActivity

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoginScreen(
                state = viewModel.uiState,
                onUserIdChange = viewModel::updateUserId,
                onPasswordChange = viewModel::updatePassword,
                onUserTypeChange = viewModel::selectUserType,
                onLogin = {
                    // 추후 viewModel.uiState.toLoginRequest()를 로그인 API에 전달합니다.
                    openMap()
                },
                onContinueAsGuest = ::openMap,
            )
        }
    }

    private fun openMap() {
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }
}
