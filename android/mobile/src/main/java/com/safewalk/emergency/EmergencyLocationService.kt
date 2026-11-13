package com.safewalk.emergency

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.safewalk.R
import com.safewalk.common.network.ApiClient
import com.safewalk.map.view.MapActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EmergencyLocationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val repository by lazy { EmergencyRepository(this) }
    private val sessionStore by lazy { EmergencySessionStore(this) }
    private var emergencyId: Long? = null
    private var uploadJob: Job? = null
    private var requestingLocation = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val id = emergencyId ?: return
            if (uploadJob?.isActive == true) return
            uploadJob = scope.launch {
                try {
                    repository.postLocation(id, location)
                    sendState(ACTION_LOCATION_SENT, id)
                } catch (error: ApiClient.ApiException) {
                    if (error.statusCode == 401 || error.statusCode == 404 || error.statusCode == 409) {
                        stopTracking(clearSession = true)
                        sendState(ACTION_TRACKING_STOPPED, id, error.message)
                    } else {
                        sendState(ACTION_LOCATION_FAILED, id, error.message)
                    }
                } catch (error: Exception) {
                    sendState(ACTION_LOCATION_FAILED, id, error.message)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_EMERGENCY_ID, 0L)?.takeIf { it > 0L }
            ?: sessionStore.activeEmergencyId()
        if (id == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        emergencyId = id
        sessionStore.setActive(id)
        startForeground(NOTIFICATION_ID, trackingNotification())
        requestLocationUpdates(id)
        return START_STICKY
    }

    private fun requestLocationUpdates(id: Long) {
        if (requestingLocation) return
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            sendState(ACTION_TRACKING_STOPPED, id, "위치 권한이 없어 SOS 위치 전송을 시작하지 못했습니다.")
            stopTracking(clearSession = false)
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
            .setMaxUpdateDelayMillis(LOCATION_INTERVAL_MS)
            .build()
        try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            requestingLocation = true
            sendState(ACTION_TRACKING_STARTED, id)
        } catch (error: SecurityException) {
            sendState(ACTION_TRACKING_STOPPED, id, "위치 권한을 확인해 주세요.")
            stopTracking(clearSession = false)
        }
    }

    private fun stopTracking(clearSession: Boolean) {
        val id = emergencyId
        if (requestingLocation) locationClient.removeLocationUpdates(locationCallback)
        requestingLocation = false
        uploadJob?.cancel()
        uploadJob = null
        if (clearSession) sessionStore.clear(id)
        emergencyId = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun trackingNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_nav_map)
        .setContentTitle("SOS 위치 공유 중")
        .setContentText("보호자에게 현재 위치를 전송하고 있습니다.")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MapActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "SOS 위치 공유",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = "SOS가 진행되는 동안 위치 전송 상태를 표시합니다." },
            )
        }
    }

    private fun sendState(action: String, id: Long, message: String? = null) {
        sendBroadcast(
            Intent(action)
                .setPackage(packageName)
                .putExtra(EXTRA_EMERGENCY_ID, id)
                .putExtra(EXTRA_MESSAGE, message),
        )
    }

    override fun onDestroy() {
        if (requestingLocation) locationClient.removeLocationUpdates(locationCallback)
        requestingLocation = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_TRACKING_STARTED = "com.safewalk.emergency.TRACKING_STARTED"
        const val ACTION_LOCATION_SENT = "com.safewalk.emergency.LOCATION_SENT"
        const val ACTION_LOCATION_FAILED = "com.safewalk.emergency.LOCATION_FAILED"
        const val ACTION_TRACKING_STOPPED = "com.safewalk.emergency.TRACKING_STOPPED"
        const val EXTRA_EMERGENCY_ID = "emergencyId"
        const val EXTRA_MESSAGE = "message"
        private const val ACTION_START = "com.safewalk.emergency.START_TRACKING"
        private const val CHANNEL_ID = "safewalk_sos_location"
        private const val NOTIFICATION_ID = 41001
        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val MIN_LOCATION_INTERVAL_MS = 4_000L

        fun start(context: Context, emergencyId: Long) {
            val intent = Intent(context, EmergencyLocationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_EMERGENCY_ID, emergencyId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, emergencyId: Long? = null) {
            EmergencySessionStore(context).clear(emergencyId)
            // 심박 이상 SOS면 워치 센서 전송도 멈춘다 (서버도 SOS가 끝나면 409로 알려 준다)
            com.safewalk.watch.SensorStreamRelay.stop(context)
            context.stopService(Intent(context, EmergencyLocationService::class.java))
        }
    }
}
