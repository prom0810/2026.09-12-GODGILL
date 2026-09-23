package com.safewalk.auth

import android.content.Context

/**
 * 로그인/회원가입으로 발급받은 JWT와 최소 사용자 정보를 기기에 저장한다.
 * 지금은 일반 SharedPreferences를 쓴다 — 운영 단계로 가면
 * androidx.security의 EncryptedSharedPreferences로 교체하는 것을 권장한다.
 */
class TokenStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("safewalk_auth", Context.MODE_PRIVATE)

    fun save(result: AuthApiResult) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, result.accessToken)
            .putLong(KEY_USER_ID, result.userId)
            .putString(KEY_EMAIL, result.email)
            .putString(KEY_USER_TYPE, result.userType)
            .apply()
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun isLoggedIn(): Boolean = !accessToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_USER_TYPE = "user_type"
    }
}
