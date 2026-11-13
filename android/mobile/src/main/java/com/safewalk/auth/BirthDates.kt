package com.safewalk.auth

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Android 23에서도 동작하는 생년월일 검증. 서버와 같은 한국 날짜 기준이다. */
object BirthDates {
    private val zone = TimeZone.getTimeZone("Asia/Seoul")

    fun parse(value: String, today: Date = Date()): Calendar? {
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false; timeZone = zone }
        val date = runCatching { format.parse(value) }.getOrNull() ?: return null
        val result = Calendar.getInstance(zone, Locale.ROOT).apply { time = date }
        if (result.get(Calendar.YEAR) < 1900 || format.format(date) > format.format(today)) return null
        return result
    }

    fun isMinor(value: String, today: Date = Date()): Boolean {
        val birthday = parse(value, today) ?: return false
        val current = Calendar.getInstance(zone, Locale.ROOT).apply { time = today }
        var age = current.get(Calendar.YEAR) - birthday.get(Calendar.YEAR)
        val currentMonthDay = current.get(Calendar.MONTH) * 100 + current.get(Calendar.DAY_OF_MONTH)
        val birthMonthDay = birthday.get(Calendar.MONTH) * 100 + birthday.get(Calendar.DAY_OF_MONTH)
        if (currentMonthDay < birthMonthDay) age--
        return age < 19
    }
}
