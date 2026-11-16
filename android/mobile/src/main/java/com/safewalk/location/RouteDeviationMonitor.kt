package com.safewalk.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.safewalk.map.view.MapActivity
import com.safewalk.watch.GuidanceStore

/**
 * 경로 안내 중 이탈 감지 → 본인 폰 알림. 판단은 폰에서만 한다 (서버·보호자에게는 보내지 않음).
 *
 * 위치는 [GpsSharingService]가 넘겨준다 (안내를 시작하면 위치 공유가 자동으로 켜져 백그라운드에서도 약 10초마다 온다).
 * 경로 좌표는 [GuidanceStore.path] (안내 시작 시 저장, 앱 재시작 복원 시 서버에서 받아 저장).
 * 기준: 경로에서 30m 초과 2회 연속 → 알림 1회. 20m 이내로 돌아오면 알림을 지운다 ([RouteDeviationTracker]).
 */
object RouteDeviationMonitor {
    /** 정확도가 이보다 나쁜 위치는 판단에 쓰지 않는다 (실내·건물 사이에서 튀는 값) */
    private const val MAX_ACCURACY_METERS = 40f
    private const val CHANNEL = "safewalk_route_deviation"
    private const val NOTIFICATION_ID = 2301

    private var routeId: Long? = null
    private var tracker: RouteDeviationTracker? = null

    @Synchronized
    fun onLocation(context: Context, location: Location) {
        val app = context.applicationContext
        val activeRouteId = GuidanceStore.activeRouteId(app)
        if (activeRouteId == null) {
            if (routeId != null) reset(app)
            return
        }
        if (activeRouteId != routeId || tracker == null) {
            // 경로 좌표가 아직 없으면(복원 중) 다음 위치에서 다시 시도
            val path = GuidanceStore.path(app, activeRouteId) ?: return
            routeId = activeRouteId
            tracker = RouteDeviationTracker(path)
            cancelNotification(app)
        }
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return
        val t = tracker ?: return
        when (t.update(location.latitude, location.longitude)) {
            RouteDeviationTracker.Event.DEVIATED -> notifyDeviated(app, t.lastDistanceMeters)
            RouteDeviationTracker.Event.RETURNED -> cancelNotification(app)
            RouteDeviationTracker.Event.NONE -> Unit
        }
    }

    /** 안내 종료·로그아웃 시 */
    @Synchronized
    fun reset(context: Context) {
        routeId = null
        tracker = null
        cancelNotification(context.applicationContext)
    }

    private fun notifyDeviated(app: Context, distanceMeters: Double) {
        if (Build.VERSION.SDK_INT >= 26) {
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "경로 이탈", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            app, NOTIFICATION_ID,
            Intent(app, MapActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "안내 경로에서 약 ${Math.round(distanceMeters)}m 벗어났어요. 지도에서 경로를 확인해 주세요."
        NotificationManagerCompat.from(app).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("경로를 벗어났어요")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    private fun cancelNotification(app: Context) {
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }
}
