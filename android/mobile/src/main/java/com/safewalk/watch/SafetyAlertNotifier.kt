package com.safewalk.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** 폰 알림. 112 는 자동 발신하지 않고, 알림 버튼을 눌러 다이얼러를 연 뒤 사용자가 직접 건다. */
object SafetyAlertNotifier {
    private const val CHANNEL = "safewalk_watch_alert"
    private const val ID_ALERT = 2001

    fun showAlert(context: Context, a: WatchAlert) {
        if (a.type == Protocol.TYPE_TEST) {
            notify(context, ID_ALERT, "✅ 워치 연결 확인", "워치에서 보낸 시험 알림이 폰에 도착했습니다", withDial = false)
            return
        }
        val title = when (a.type) {
            Protocol.TYPE_SOS_MANUAL -> "🚨 워치 SOS 요청"
            Protocol.TYPE_FALL -> "🚨 낙상 감지"
            Protocol.TYPE_VITALS_HIGH -> "🚨 심박수 매우 높음"
            Protocol.TYPE_VITALS_LOW -> "🚨 심박수 매우 낮음"
            Protocol.TYPE_VITALS_SPIKE -> "🚨 심박수 급변"
            else -> "🚨 긴급 알림"
        }
        val lat = WatchState.lastLat
        val lon = WatchState.lastLon
        val loc = if (lat != null && lon != null)
            "위치: https://map.kakao.com/link/map/현재위치,$lat,$lon" else "위치 정보 없음"
        val bpm = a.bpm?.takeIf { !it.isNaN() }?.let { " · 심박 ${"%.0f".format(it)}bpm" } ?: ""
        notify(context, ID_ALERT, title, "$loc$bpm", withDial = true)
    }

    private fun notify(context: Context, id: Int, title: String, text: String, withDial: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "워치 안전 알림", NotificationManager.IMPORTANCE_HIGH))
        }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
        if (withDial) {
            val dial = PendingIntent.getActivity(
                context, 112, Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(android.R.drawable.ic_menu_call, "112 전화 연결", dial)
        }
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) NotificationManagerCompat.from(context).notify(id, b.build())
    }
}
