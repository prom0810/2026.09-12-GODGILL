package com.safewalk.wear.sos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.safewalk.wear.presentation.MainActivity

/**
 * 화면이 꺼져 있어도 카운트다운/긴급 화면이 뜨도록 하는 높은 중요도 알림(fullScreenIntent).
 * 알림 권한(POST_NOTIFICATIONS)이 없으면 조용히 건너뛴다(진동/경보음은 그대로 동작).
 * 기기에 따라 fullScreenIntent 가 헤드업 알림으로만 보일 수 있다 → 실기 테스트로 확인.
 */
object AlertNotifier {
    private const val CHANNEL = "godgill_alert"
    private const val ID = 2

    fun show(context: Context, title: String, text: String) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "긴급 알림", NotificationManager.IMPORTANCE_HIGH)
            )
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build()
            nm.notify(ID, n)
        } catch (_: Exception) { }
    }

    fun clear(context: Context) {
        try { context.getSystemService(NotificationManager::class.java).cancel(ID) } catch (_: Exception) { }
    }
}
