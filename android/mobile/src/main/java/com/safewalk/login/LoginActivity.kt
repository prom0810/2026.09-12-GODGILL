package com.safewalk.login

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.AuthenticatedApiClient
import com.safewalk.map.view.MapActivity
import com.safewalk.guardian.GuardianActivity
import com.safewalk.notification.view.NotificationActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                onGuardianNameChange = viewModel::updateGuardianName,
                onGuardianPhoneChange = viewModel::updateGuardianPhone,
                onTermsAgreementChange = viewModel::setTermsAgreement,
                onPrivacyAgreementChange = viewModel::setPrivacyAgreement,
                onLocationAgreementChange = viewModel::setLocationAgreement,
                onAllAgreementsChange = viewModel::setAllAgreements,
                onUserTypeChange = viewModel::selectUserType,
                onNotificationPermissionChange = viewModel::setNotificationPermission,
                onActivityPermissionChange = viewModel::setActivityPermission,
                onContactsPermissionChange = viewModel::setContactsPermission,
                onOpenSignUp = viewModel::openSignUp,
                onContinueToTerms = viewModel::continueToTerms,
                onContinueToUserType = viewModel::continueToUserType,
                onContinueToPermissions = viewModel::continueToPermissions,
                onBack = viewModel::goBack,
                onLogin = { viewModel.login(onSuccess = ::openAfterAuth) },
                onContinueAsGuest = ::openMap,
                onStart = { viewModel.signup(onSuccess = ::requestSelectedPermissions) },
                onSkipPermissions = { viewModel.signup(onSuccess = ::openMap) },
            )
        }
        restoreSession()
    }

    private fun restoreSession() {
        if (!TokenStore(this).isLoggedIn()) return
        lifecycleScope.launch {
            val active = runCatching {
                withContext(Dispatchers.IO) { AuthenticatedApiClient(this@LoginActivity).getObject("/api/users/me") }
            }.isSuccess
            if (active) openAfterAuth()
        }
    }

    private fun openAfterAuth() {
        val eventType = intent.getStringExtra("type").orEmpty()
        val category = intent.getStringExtra("category").orEmpty()
        val target = when {
            category == "GUARDIAN" || eventType.startsWith("GUARDIAN_") -> GuardianActivity::class.java
            eventType.isNotBlank() -> NotificationActivity::class.java
            else -> MapActivity::class.java
        }
        startActivity(Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
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
        startActivity(
            Intent(this, MapActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        finish()
    }
}
