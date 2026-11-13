package com.safewalk.emergency

import android.content.Context
import android.location.Location
import com.safewalk.common.network.ApiClient
import com.safewalk.common.network.AuthenticatedApiClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EmergencyRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun start(location: Location?): StartedEmergency {
        val request = JSONObject().put("source", "MOBILE")
        location?.let {
            request.put("lat", it.latitude)
            request.put("lng", it.longitude)
            if (it.hasAccuracy()) request.put("accuracy", it.accuracy.toDouble())
        }
        return try {
            api.postObject("/api/emergencies", request).toStartedEmergency()
        } catch (error: ApiClient.ApiException) {
            val existingId = (error.responseData as? JSONObject)?.optLong("emergencyId")?.takeIf { it > 0L }
            if (error.statusCode == 409 && existingId != null) {
                StartedEmergency(existingId, "OCCURRED", 0, 0, recoveredExisting = true)
            } else {
                throw error
            }
        }
    }

    /**
     * 워치 자동 감지 SOS (경로 안내 중에만). type = "FALL" 또는 "ABNORMAL_HEART_RATE".
     * bpm = 워치가 감지할 때 잰 심박 (심박 이상이면 bpm_log 발생 당시 심박으로 저장됨).
     * 워치·경로·구간은 서버가 정한다. 이미 열린 SOS가 있으면(409) 그 사건을 이어서 쓴다.
     */
    fun startAutoDetected(type: String, location: Location?, bpm: Int? = null): StartedEmergency {
        val request = JSONObject().put("type", type).put("source", "WEARABLE")
        bpm?.takeIf { it in 20..250 }?.let { request.put("bpm", it) }
        location?.let {
            request.put("lat", it.latitude)
            request.put("lng", it.longitude)
            if (it.hasAccuracy()) request.put("accuracy", it.accuracy.toDouble())
        }
        return try {
            api.postObject("/api/emergencies", request).toStartedEmergency()
        } catch (error: ApiClient.ApiException) {
            val existingId = (error.responseData as? JSONObject)?.optLong("emergencyId")?.takeIf { it > 0L }
            if (error.statusCode == 409 && existingId != null) {
                StartedEmergency(existingId, "OCCURRED", 0, 0, recoveredExisting = true)
            } else {
                throw error
            }
        }
    }

    fun active(): EmergencyDetail? = api.getObject("/api/emergencies/active")
        .takeIf { it.length() > 0 }
        ?.toEmergencyDetail()

    fun detail(emergencyId: Long): EmergencyDetail =
        api.getObject("/api/emergencies/$emergencyId").toEmergencyDetail()

    fun shared(): List<SharedEmergency> {
        val data = api.getArray("/api/emergencies/shared")
        return (0 until data.length()).map { data.getJSONObject(it).toSharedEmergency() }
    }

    fun latestLocation(emergencyId: Long): EmergencyLocation? =
        api.getObject("/api/emergencies/$emergencyId/locations/latest")
            .takeIf { it.length() > 0 }
            ?.toEmergencyLocation()

    fun postLocation(emergencyId: Long, location: Location) {
        val body = JSONObject()
            .put("lat", location.latitude)
            .put("lng", location.longitude)
            .put("recordedAt", formatRecordedAt(location.time))
        if (location.hasAccuracy()) body.put("accuracy", location.accuracy.toDouble())
        api.post("/api/emergencies/$emergencyId/locations", body)
    }

    fun resolve(emergencyId: Long, status: String = "RESOLVED") {
        api.patchObject(
            "/api/emergencies/$emergencyId",
            JSONObject().put("status", status),
        )
    }

    private fun JSONObject.toStartedEmergency(): StartedEmergency {
        val notified = optJSONObject("notified") ?: JSONObject()
        return StartedEmergency(
            emergencyId = getLong("emergencyId"),
            status = getString("status"),
            pushGuardians = notified.optInt("pushGuardians"),
            locationSharedGuardians = notified.optInt("locationSharedGuardians"),
        )
    }

    private fun formatRecordedAt(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))
}
