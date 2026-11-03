package com.safewalk.emergency

import org.json.JSONObject

data class EmergencyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val recordedAt: String,
)

data class EmergencyDetail(
    val emergencyId: Long,
    val userName: String,
    val status: String,
    val viewer: String,
    val canViewLocation: Boolean,
    val initialLocation: EmergencyLocation?,
)

data class SharedEmergency(
    val emergencyId: Long,
    val userName: String,
    val occurredAt: String,
    val canViewLocation: Boolean,
)

data class StartedEmergency(
    val emergencyId: Long,
    val status: String,
    val pushGuardians: Int,
    val locationSharedGuardians: Int,
    val recoveredExisting: Boolean = false,
)

internal fun JSONObject.toEmergencyLocation() = EmergencyLocation(
    latitude = getDouble("lat"),
    longitude = getDouble("lng"),
    accuracyMeters = if (has("accuracy") && !isNull("accuracy")) getDouble("accuracy") else null,
    recordedAt = optString("recordedAt"),
)

internal fun JSONObject.toEmergencyDetail(): EmergencyDetail {
    val initial = optJSONObject("initialLocation")
    return EmergencyDetail(
        emergencyId = getLong("emergencyId"),
        userName = optString("userName", "사용자"),
        status = getString("status"),
        viewer = optString("viewer"),
        canViewLocation = optBoolean("canViewLocation"),
        initialLocation = initial?.let {
            EmergencyLocation(
                latitude = it.getDouble("lat"),
                longitude = it.getDouble("lng"),
                accuracyMeters = null,
                recordedAt = optString("occurredAt"),
            )
        },
    )
}

internal fun JSONObject.toSharedEmergency() = SharedEmergency(
    emergencyId = getLong("emergencyId"),
    userName = optString("userName", "사용자"),
    occurredAt = optString("occurredAt"),
    canViewLocation = optBoolean("canViewLocation"),
)
