package com.safewalk.auth

import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BirthDatesTest {
    private fun day(value: String) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Seoul")
    }.parse(value)!!

    @Test
    fun `signup validates real dates and rejects future dates`() {
        val today = day("2026-11-13")
        listOf("", "2012-02-30", "2012-2-02", "2026-11-14", "1899-12-31").forEach {
            assertNull(it, BirthDates.parse(it, today))
        }
        assertNotNull(BirthDates.parse("2012-02-29", today))
    }

    @Test
    fun `nineteen year guardian rule stays independent of fourteen year route band`() {
        val today = day("2026-11-13")
        assertTrue(BirthDates.isMinor("2012-11-13", today))
        assertTrue(BirthDates.isMinor("2007-11-14", today))
        assertFalse(BirthDates.isMinor("2007-11-13", today))
    }
}
