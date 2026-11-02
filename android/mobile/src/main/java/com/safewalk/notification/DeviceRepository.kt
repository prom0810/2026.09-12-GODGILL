package com.safewalk.notification

import android.content.Context
import com.safewalk.common.network.AuthenticatedApiClient
import org.json.JSONObject

class DeviceRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun register(fcmToken: String) {
        api.putObject("/api/users/me/devices", JSONObject().apply {
            put("fcmToken", fcmToken)
            put("deviceType", "ANDROID")
        })
    }

    fun unregister(fcmToken: String) {
        api.delete("/api/users/me/devices", JSONObject().put("fcmToken", fcmToken))
    }
}
