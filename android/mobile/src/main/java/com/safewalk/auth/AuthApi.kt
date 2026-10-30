package com.safewalk.auth

import com.safewalk.common.network.ApiClient
import org.json.JSONArray
import org.json.JSONObject

/** /api/auth/signup, /api/auth/login 호출. 블로킹 호출이므로 백그라운드 스레드에서만 호출해야 한다. */
class AuthApi {
    fun signup(request: SignupApiRequest): AuthApiResult {
        val body = JSONObject().apply {
            put("email", request.email)
            put("password", request.password)
            put("name", request.name)
            put("phone", request.phone)
            put("userType", request.userType)
            put("guardians", JSONArray().apply {
                request.guardians.forEach { guardian ->
                    put(JSONObject().apply {
                        put("name", guardian.name)
                        put("phone", guardian.phone)
                        put("relationship", guardian.relationship)
                        put("isPrimary", guardian.isPrimary)
                        put("canViewLocation", guardian.canViewLocation)
                    })
                }
            })
        }
        return ApiClient.postJson("/api/auth/signup", body).toAuthApiResult()
    }

    fun login(request: LoginApiRequest): AuthApiResult {
        val body = JSONObject().apply {
            put("email", request.email)
            put("password", request.password)
        }
        return ApiClient.postJson("/api/auth/login", body).toAuthApiResult()
    }

    private fun JSONObject.toAuthApiResult() = AuthApiResult(
        userId = getLong("userId"),
        email = getString("email"),
        userType = getString("userType"),
        accessToken = getString("accessToken"),
    )
}
