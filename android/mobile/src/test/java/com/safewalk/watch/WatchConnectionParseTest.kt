package com.safewalk.watch

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchConnectionParseTest {

    @Test
    fun `watch info needs device id`() {
        val info = parseWatchInfo("node-1", JSONObject("""{"deviceId":"a1b2c3","model":"SM-R910","appVersion":"1.0"}"""))
        assertEquals(WatchInfo("node-1", "a1b2c3", "SM-R910", "1.0"), info)

        assertNull(parseWatchInfo("node-1", JSONObject("""{"deviceId":"  ","model":"SM-R910"}""")))
    }

    @Test
    fun `registered wearables keep connection flag`() {
        val list = parseRegisteredWearables(JSONArray(
            """[{"wearableId":3,"deviceType":"WEAR_OS","deviceIdentifier":"a1b2c3","deviceName":"SM-R910","isActive":true},
                {"wearableId":4,"deviceType":"WEAR_OS","deviceIdentifier":"d4e5f6","deviceName":null,"isActive":false}]""",
        ))

        assertEquals(2, list.size)
        assertEquals(RegisteredWearable(3L, "a1b2c3", "SM-R910", true), list[0])
        assertNull(list[1].deviceName)
        assertFalse(list[1].isActive)
    }

    @Test
    fun `switch conflict carries current wearable`() {
        val data = JSONObject(
            """{"reason":"SWITCH_REQUIRED","currentWearable":{"wearableId":3,"deviceIdentifier":"a1b2c3","deviceName":"SM-R910","isActive":true}}""",
        )
        val conflict = parseConnectionConflict(data, "다른 워치가 연결되어 있어요.")

        assertEquals("SWITCH_REQUIRED", conflict?.reason)
        assertEquals(3L, conflict?.current?.wearableId)
        assertTrue(conflict?.current?.isActive == true)
    }

    @Test
    fun `other account conflict hides owner and non connection 409 is ignored`() {
        val conflict = parseConnectionConflict(
            JSONObject("""{"reason":"IN_USE_BY_OTHER_ACCOUNT","currentWearable":null}"""), "다른 계정에서 연결 중인 워치예요.",
        )
        assertEquals("IN_USE_BY_OTHER_ACCOUNT", conflict?.reason)
        assertNull(conflict?.current)

        // SOS 중복(409 + emergencyId)은 연결 충돌이 아니다
        assertNull(parseConnectionConflict(JSONObject("""{"emergencyId":31}"""), "이미 진행 중인 SOS가 있습니다."))
        assertNull(parseConnectionConflict(null, ""))
    }
}
