package com.safewalk.location

import android.content.Context
import android.location.Location
import com.safewalk.common.network.AuthenticatedApiClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GpsSharingRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun start(): String = api.postObject("/api/locations/sessions").getString("sessionId")

    fun append(sessionId: String, location: Location) {
        val body = JSONObject()
            .put("lat", location.latitude)
            .put("lng", location.longitude)
            .put("recordedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(location.time)))
        if (location.hasAccuracy()) body.put("accuracy", location.accuracy.toDouble())
        api.post("/api/locations/sessions/$sessionId/logs", body)
    }

    fun close(sessionId: String) { api.delete("/api/locations/sessions/$sessionId") }
}
