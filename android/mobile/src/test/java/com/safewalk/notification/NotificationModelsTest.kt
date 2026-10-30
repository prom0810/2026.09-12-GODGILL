package com.safewalk.notification

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationModelsTest {
    @Test
    fun `guardian notification keeps navigation reference`() {
        val item = JSONObject(
            """{
                "notificationId": 11,
                "type": "GUARDIAN",
                "title": "보호자 연결 요청",
                "content": "연결 요청이 도착했습니다.",
                "referenceType": "GUARDIAN",
                "referenceId": 7,
                "read": false,
                "createdAt": "2026-10-30T09:00:00+09:00"
            }""",
        ).toNotificationItem()

        assertEquals("GUARDIAN", item.referenceType)
        assertEquals(7L, item.referenceId)
        assertFalse(item.read)
    }
}
