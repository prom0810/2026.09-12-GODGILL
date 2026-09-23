package com.safewalk.signup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.safewalk.map.MapActivity

class SignupActivity : ComponentActivity() {
    private val viewModel: SignupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignupScreen(
                state = viewModel.uiState,
                onEmailChange = viewModel::updateEmail,
                onPasswordChange = viewModel::updatePassword,
                onUserTypeChange = viewModel::selectUserType,
                onSignup = { viewModel.signup(onSuccess = ::openMap) },
                onBackToLogin = { finish() },
            )
        }
    }

    private fun openMap() {
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }
}
