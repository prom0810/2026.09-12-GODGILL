package com.safewalk.guardian

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianModelsTest {
    @Test
    fun `guardian v3 response maps primary status and location permission`() {
        val guardian = JSONObject(
            """{
                "guardianId": 7,
                "name": "김보호",
                "phone": "010-1234-5678",
                "relationship": "PARENT",
                "relationshipLabel": "부모",
                "isPrimary": true,
                "registrationStatus": "CONNECTED",
                "canViewLocation": true
            }""",
        ).toGuardian()

        assertEquals(7L, guardian.id)
        assertEquals("CONNECTED", guardian.status)
        assertTrue(guardian.isPrimary)
        assertTrue(guardian.canViewLocation)
    }
}
