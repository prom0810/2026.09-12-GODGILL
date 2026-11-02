package com.safewalk.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 만료 시간이 있는 제보 사진 URL을 목록·상세 화면 크기에 맞춰 읽는다. */
object ReportImageLoader {
    suspend fun load(url: String, maxWidth: Int = 900, maxHeight: Int = 600): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                try {
                    val bytes = connection.inputStream.use { it.readBytes() }
                    decodeSampled(bytes, maxWidth, maxHeight)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    private fun decodeSampled(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxWidth || bounds.outHeight / sampleSize > maxHeight) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }
}
