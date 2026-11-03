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
import com.safewalk.emergency.EmergencyLocationService
import com.safewalk.emergency.EmergencyMapActivity

class SafeWalkMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = FcmTokenManager.onNewToken(this, token)

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "SafeWalk 알림"
        val body = message.notification?.body ?: message.data["body"] ?: "새로운 안전 알림이 도착했습니다."
        showNotification(title, body, message.data)
        if (message.data["type"] == "EMERGENCY_ENDED") {
            message.data["emergencyId"]?.toLongOrNull()?.let {
                EmergencyLocationService.stop(this, it)
            }
        }
        NotificationEvents.notifyIncoming()
    }

    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "안전 및 보호자 알림", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val eventType = data["type"].orEmpty()
        val isGuardian = data["category"] == "GUARDIAN" || eventType.startsWith("GUARDIAN_")
        val isEmergency = data["category"] == "EMERGENCY" || eventType.startsWith("EMERGENCY_")
        val target = when {
            isEmergency -> EmergencyMapActivity::class.java
            isGuardian -> GuardianActivity::class.java
            else -> NotificationActivity::class.java
        }
        val emergencyId = data["emergencyId"]?.toLongOrNull()
        val pendingIntent = PendingIntent.getActivity(
            this,
            emergencyId?.hashCode() ?: 0,
            Intent(this, target).apply {
                flags = if (isEmergency) {
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                } else {
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                data.forEach { (key, value) -> putExtra(key, value) }
                emergencyId?.let { putExtra(EmergencyMapActivity.EXTRA_EMERGENCY_ID, it) }
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
