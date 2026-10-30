package com.safewalk.notification

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.safewalk.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FcmTokenManager {
    private const val PREFS = "safewalk_push"
    private const val KEY_TOKEN = "fcm_token"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sync(context: Context) {
        val appContext = context.applicationContext
        if (!TokenStore(appContext).isLoggedIn() || FirebaseApp.getApps(appContext).isEmpty()) return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            saveToken(appContext, token)
            scope.launch { runCatching { DeviceRepository(appContext).register(token) } }
        }
    }

    fun onNewToken(context: Context, token: String) {
        val appContext = context.applicationContext
        saveToken(appContext, token)
        if (TokenStore(appContext).isLoggedIn()) {
            scope.launch { runCatching { DeviceRepository(appContext).register(token) } }
        }
    }

    suspend fun unregisterCurrent(context: Context) {
        val appContext = context.applicationContext
        val token = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null).orEmpty()
        if (token.isNotBlank() && TokenStore(appContext).isLoggedIn()) {
            runCatching { withContext(Dispatchers.IO) { DeviceRepository(appContext).unregister(token) } }
        }
    }

    private fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TOKEN, token).apply()
    }
}
