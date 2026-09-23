package com.safewalk.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.safewalk.map.MapActivity
import com.safewalk.signup.SignupActivity

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
                onLogin = { viewModel.login(onSuccess = ::openMap) },
                onContinueAsGuest = ::openMap,
                onGoToSignup = ::openSignup,
            )
        }
    }

    private fun openMap() {
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }

    private fun openSignup() {
        startActivity(Intent(this, SignupActivity::class.java))
    }
}
