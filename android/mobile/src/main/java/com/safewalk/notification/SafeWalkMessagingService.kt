package com.safewalk.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.safewalk.R
import com.safewalk.notification.view.NotificationActivity
import com.safewalk.guardian.GuardianActivity

class SafeWalkMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = FcmTokenManager.onNewToken(this, token)

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "SafeWalk 알림"
        val body = message.notification?.body ?: message.data["body"] ?: "새로운 안전 알림이 도착했습니다."
        showNotification(title, body, message.data)
        NotificationEvents.notifyIncoming()
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "안전 및 보호자 알림", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val isGuardian = data["category"] == "GUARDIAN" || data["type"].orEmpty().startsWith("GUARDIAN_")
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, if (isGuardian) GuardianActivity::class.java else NotificationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification) }
    }

    companion object {
        private const val CHANNEL_ID = "safewalk_alerts"
    }
}
