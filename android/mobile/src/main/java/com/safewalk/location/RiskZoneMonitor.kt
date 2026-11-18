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
import com.safewalk.map.model.RouteSafetyScorer
import com.safewalk.map.view.MapActivity
import com.safewalk.watch.GuidanceStore
import com.safewalk.watch.Protocol
import com.safewalk.watch.WatchGuideAlert
import kotlin.math.roundToLong

/**
 * 경로 안내 중 위험구역 접근(경계 50m 전)·진입 → 본인 폰 알림. 판단은 폰에서만 한다 (서버·보호자에게 보내지 않음).
 *
 * 위치는 [GpsSharingService]가 넘겨준다 (안내 중 자동으로 켜짐). 구역은 [RouteSafetyScorer.riskZones]
 * (서버 risk_area 캐시, 없으면 목업) — 경로 안전도 감점과 같은 데이터다.
 * 안내가 바뀌거나 구역 목록이 바뀌면 판단 상태를 새로 만든다.
 *
 * 연결된 워치에도 경보를 보낸다 ([WatchGuideAlert]): 접근 = 짧은 비프, 진입 = 강한 경보음(3~5초).
 * 폰 알림은 워치로 전달되지 않게 한다(setLocalOnly) — 워치에서 두 번 울리지 않도록.
 */
object RiskZoneMonitor {
    private const val MAX_ACCURACY_METERS = 40f
    private const val CHANNEL = "safewalk_risk_zone"
    private const val NOTIFICATION_ID = 2302

    private var routeId: Long? = null
    private var zones: List<*>? = null
    private var tracker: RiskZoneTracker? = null

    @Synchronized
    fun onLocation(context: Context, location: Location) {
        val app = context.applicationContext
        val activeRouteId = GuidanceStore.activeRouteId(app)
        if (activeRouteId == null) {
            if (routeId != null) reset(app)
            return
        }
        val currentZones = RouteSafetyScorer.riskZones
        if (activeRouteId != routeId || tracker == null) {
            routeId = activeRouteId
            zones = currentZones
            tracker = RiskZoneTracker(currentZones)
        } else if (currentZones !== zones) {
            // 안내 중 서버에서 구역 목록이 갱신됨 → 같은 구역은 다시 알리지 않게 상태를 이어받는다
            val old = tracker
            zones = currentZones
            tracker = RiskZoneTracker(currentZones).also { new -> old?.let { new.inheritFrom(it) } }
        }
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return
        val alerts = tracker?.update(location.latitude, location.longitude) ?: return
        // 한 번에 여러 개면 진입을 먼저 (알림은 하나만 갱신해 보여 준다)
        alerts.sortedBy { if (it.kind == RiskZoneTracker.Kind.ENTERED) 0 else 1 }.firstOrNull()?.let {
            alertWatch(app, it)
            notify(app, it)
        }
    }

    /** 안내 종료·로그아웃 시 */
    @Synchronized
    fun reset(context: Context) {
        val hadRoute = routeId != null
        routeId = null
        zones = null
        tracker = null
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
        if (hadRoute) {
            WatchGuideAlert.clear(context.applicationContext, Protocol.GUIDE_RISK_APPROACH)
            WatchGuideAlert.clear(context.applicationContext, Protocol.GUIDE_RISK_ENTER)
        }
    }

    private fun alertWatch(app: Context, alert: RiskZoneTracker.Alert) {
        when (alert.kind) {
            RiskZoneTracker.Kind.APPROACHING -> WatchGuideAlert.send(
                app, Protocol.GUIDE_RISK_APPROACH,
                "위험구역이 가까워요",
                "약 ${alert.distanceToEdgeMeters.roundToLong().coerceAtLeast(1)}m 앞 위험구역(${alert.zone.label})",
            )
            RiskZoneTracker.Kind.ENTERED -> WatchGuideAlert.send(
                app, Protocol.GUIDE_RISK_ENTER,
                "위험구역에 들어왔어요",
                "${alert.zone.label} · 밝고 사람 많은 길로 이동하세요",
            )
        }
    }

    private fun notify(app: Context, alert: RiskZoneTracker.Alert) {
        if (Build.VERSION.SDK_INT >= 26) {
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "위험구역 알림", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val (title, text) = when (alert.kind) {
            RiskZoneTracker.Kind.APPROACHING -> "⚠️ 위험구역이 가까워요" to
                "약 ${alert.distanceToEdgeMeters.roundToLong().coerceAtLeast(1)}m 앞에 위험구역(${alert.zone.label})이 있어요. 주변을 살피며 이동해 주세요."
            RiskZoneTracker.Kind.ENTERED -> "⚠️ 위험구역에 들어왔어요" to
                "위험구역(${alert.zone.label}) 안이에요. 밝고 사람이 많은 길로 이동하고, 위급하면 SOS를 눌러 주세요."
        }
        val open = PendingIntent.getActivity(
            app, NOTIFICATION_ID,
            Intent(app, MapActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        NotificationManagerCompat.from(app).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
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
}
