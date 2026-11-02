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
    fun signup(
        email: String,
        password: String,
        name: String,
        phone: String,
        userType: String,
        guardians: List<GuardianSignupApiRequest> = emptyList(),
    ): AuthApiResult {
        val result = api.signup(
            SignupApiRequest(email, password, name, phone, userType, guardians),
        )
        tokenStore.save(result)
        return result
    }

    /** 이전 별도 회원가입 화면용. 화면이 정리되기 전까지 컴파일 호환만 유지한다. */
    fun signup(email: String, password: String, userType: String): AuthApiResult =
        signup(email, password, email.substringBefore('@'), "", userType)

    fun login(email: String, password: String): AuthApiResult {
        val result = api.login(LoginApiRequest(email = email, password = password))
        tokenStore.save(result)
        return result
    }

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    fun logout() = tokenStore.clear()
}
