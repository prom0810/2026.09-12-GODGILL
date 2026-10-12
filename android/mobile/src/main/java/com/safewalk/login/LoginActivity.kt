package com.safewalk.login

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.safewalk.map.view.MapActivity

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { openMap() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoginScreen(
                state = viewModel.uiState,
                onLoginEmailChange = viewModel::updateLoginEmail,
                onLoginPasswordChange = viewModel::updateLoginPassword,
                onNameChange = viewModel::updateName,
                onSignUpEmailChange = viewModel::updateSignUpEmail,
                onSignUpPasswordChange = viewModel::updateSignUpPassword,
                onPhoneNumberChange = viewModel::updatePhoneNumber,
                onTermsAgreementChange = viewModel::setTermsAgreement,
                onUserTypeChange = viewModel::selectUserType,
                onNotificationPermissionChange = viewModel::setNotificationPermission,
                onActivityPermissionChange = viewModel::setActivityPermission,
                onContactsPermissionChange = viewModel::setContactsPermission,
                onOpenSignUp = viewModel::openSignUp,
                onContinueToUserType = viewModel::continueToUserType,
                onContinueToPermissions = viewModel::continueToPermissions,
                onBack = viewModel::goBack,
                onLogin = {
                    // TODO 백엔드 구현 후 viewModel.uiState.toLoginRequest()를 전달합니다.
                    openMap()
                },
                onContinueAsGuest = ::openMap,
                onStart = ::requestSelectedPermissions,
                onSkipPermissions = ::openMap,
            )
        }
    }

    private fun requestSelectedPermissions() {
        val state = viewModel.uiState
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (state.notificationPermission && Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (state.activityPermission && Build.VERSION.SDK_INT >= 29) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (state.contactsPermission) add(Manifest.permission.READ_CONTACTS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openMap() {
        startActivity(Intent(this, MapActivity::class.java))
        finish()
    }
}
