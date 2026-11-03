package com.safewalk.emergency

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyModelsTest {
    @Test
    fun `location event preserves coordinates accuracy and measured time`() {
        val location = JSONObject(
            """{"lat":36.8101,"lng":127.1468,"accuracy":7.5,"recordedAt":"2026-11-02T01:02:03Z"}""",
        ).toEmergencyLocation()

        assertEquals(36.8101, location.latitude, 0.000001)
        assertEquals(127.1468, location.longitude, 0.000001)
        assertEquals(7.5, location.accuracyMeters!!, 0.001)
        assertEquals("2026-11-02T01:02:03Z", location.recordedAt)
    }

    @Test
    fun `detail parses guardian permission and optional initial location`() {
        val detail = JSONObject(
            """{
                "emergencyId":19,
                "userName":"김안심",
                "status":"OCCURRED",
                "occurredAt":"2026-11-02T01:00:00Z",
                "viewer":"GUARDIAN",
                "canViewLocation":true,
                "initialLocation":{"lat":36.8,"lng":127.1}
            }""",
        ).toEmergencyDetail()

        assertEquals(19L, detail.emergencyId)
        assertEquals("GUARDIAN", detail.viewer)
        assertTrue(detail.canViewLocation)
        assertEquals(36.8, detail.initialLocation!!.latitude, 0.001)
    }

    @Test
    fun `detail without location consent has no initial location`() {
        val detail = JSONObject(
            """{
                "emergencyId":20,
                "userName":"김안심",
                "status":"OCCURRED",
                "viewer":"GUARDIAN",
                "canViewLocation":false,
                "initialLocation":null
            }""",
        ).toEmergencyDetail()

        assertFalse(detail.canViewLocation)
        assertNull(detail.initialLocation)
    }
}
