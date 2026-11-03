package com.safewalk.emergency

import android.content.Context

class EmergencySessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("safewalk_emergency", Context.MODE_PRIVATE)

    fun activeEmergencyId(): Long? = prefs.getLong(KEY_ACTIVE_ID, 0L).takeIf { it > 0L }

    fun setActive(emergencyId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_ID, emergencyId).apply()
    }

    fun clear(emergencyId: Long? = null) {
        if (emergencyId == null || activeEmergencyId() == emergencyId) {
            prefs.edit().remove(KEY_ACTIVE_ID).apply()
        }
    }

    private companion object {
        const val KEY_ACTIVE_ID = "active_emergency_id"
    }
}
