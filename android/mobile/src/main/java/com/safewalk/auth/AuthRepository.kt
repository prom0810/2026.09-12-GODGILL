package com.safewalk.auth

import android.content.Context

/**
 * 화면(ViewModel)과 네트워크(AuthApi) / 로컬 저장소(TokenStore) 사이를 잇는다.
 * signup/login 모두 블로킹 호출이므로 반드시 백그라운드 스레드(Dispatchers.IO)에서 불러야 한다.
 */
class AuthRepository(
    context: Context,
    private val api: AuthApi = AuthApi(),
    private val tokenStore: TokenStore = TokenStore(context),
) {
    fun signup(email: String, password: String, userType: String): AuthApiResult {
        val result = api.signup(SignupApiRequest(email = email, password = password, userType = userType))
        tokenStore.save(result)
        return result
    }

    fun login(email: String, password: String): AuthApiResult {
        val result = api.login(LoginApiRequest(email = email, password = password))
        tokenStore.save(result)
        return result
    }

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    fun logout() = tokenStore.clear()
}
