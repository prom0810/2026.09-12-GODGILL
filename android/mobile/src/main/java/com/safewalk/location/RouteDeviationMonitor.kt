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
import com.safewalk.watch.Protocol
import com.safewalk.watch.WatchGuideAlert

/**
 * 경로 안내 중 이탈 감지 → 본인 폰 알림. 판단은 폰에서만 한다 (서버·보호자에게는 보내지 않음).
 *
 * 위치는 [GpsSharingService]가 넘겨준다 (안내를 시작하면 위치 공유가 자동으로 켜져 백그라운드에서도 약 10초마다 온다).
 * 경로 좌표는 [GuidanceStore.path] (안내 시작 시 저장, 앱 재시작 복원 시 서버에서 받아 저장).
 * 기준: 경로에서 30m 초과 2회 연속 → 알림 1회. 20m 이내로 돌아오면 알림을 지운다 ([RouteDeviationTracker]).
 *
 * 연결된 워치에도 경보를 보낸다 ([WatchGuideAlert]). 이탈이 이어지면 [WATCH_REPEAT_INTERVAL_MS]마다
 * 최대 [WATCH_MAX_REPEATS]번 더 울리고(폰 알림은 다시 띄우지 않음), 경로로 돌아오거나 안내가 끝나면 워치 경보를 끈다.
 * 폰 알림은 워치로 전달되지 않게 한다(setLocalOnly) — 워치에서 두 번 울리지 않도록.
 */
object RouteDeviationMonitor {
    /** 정확도가 이보다 나쁜 위치는 판단에 쓰지 않는다 (실내·건물 사이에서 튀는 값) */
    private const val MAX_ACCURACY_METERS = 40f
    private const val CHANNEL = "safewalk_route_deviation"
    private const val NOTIFICATION_ID = 2301
    private const val WATCH_REPEAT_INTERVAL_MS = 60_000L
    private const val WATCH_MAX_REPEATS = 2

    private var routeId: Long? = null
    private var tracker: RouteDeviationTracker? = null
    private var lastWatchAlertMs = 0L
    private var watchRepeats = 0

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
            RouteDeviationTracker.Event.DEVIATED -> {
                watchRepeats = 0
                alertWatch(app, t.lastDistanceMeters)
                notifyDeviated(app, t.lastDistanceMeters)
            }
            RouteDeviationTracker.Event.RETURNED -> {
                cancelNotification(app)
                WatchGuideAlert.clear(app, Protocol.GUIDE_ROUTE_DEVIATION)
            }
            RouteDeviationTracker.Event.NONE -> {
                // 이탈이 계속되면 워치만 다시 울린다
                if (t.deviated && watchRepeats < WATCH_MAX_REPEATS &&
                    System.currentTimeMillis() - lastWatchAlertMs >= WATCH_REPEAT_INTERVAL_MS
                ) {
                    watchRepeats++
                    alertWatch(app, t.lastDistanceMeters)
                }
            }
        }
    }

    /** 안내 종료·로그아웃 시 */
    @Synchronized
    fun reset(context: Context) {
        val hadRoute = routeId != null
        routeId = null
        tracker = null
        watchRepeats = 0
        cancelNotification(context.applicationContext)
        if (hadRoute) WatchGuideAlert.clear(context.applicationContext, Protocol.GUIDE_ROUTE_DEVIATION)
    }

    private fun alertWatch(app: Context, distanceMeters: Double) {
        lastWatchAlertMs = System.currentTimeMillis()
        WatchGuideAlert.send(
            app, Protocol.GUIDE_ROUTE_DEVIATION,
            "경로를 벗어났어요",
            "안내 경로에서 약 ${Math.round(distanceMeters)}m 벗어났어요",
        )
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
                .setLocalOnly(true) // 워치는 WatchGuideAlert로 따로 울린다
                .setAutoCancel(true)
                .setContentIntent(open)
                .build(),
        )
    }

    private fun cancelNotification(app: Context) {
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID)
    }
}
