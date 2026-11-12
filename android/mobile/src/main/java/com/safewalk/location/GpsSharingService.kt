package com.safewalk.location

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** Foreground collection continues while the map is away; sharing ends explicitly. */
class GpsSharingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val repository by lazy { GpsSharingRepository(this) }
    private val prefs by lazy { getSharedPreferences("gps_sharing", MODE_PRIVATE) }
    private var requesting = false
    private var stopping = false
    private var upload: Job? = null
    private var stopJob: Job? = null
    private var permissionWatch: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fix = result.lastLocation ?: return
            if (stopping || upload?.isActive == true) return
            upload = scope.launch {
                try {
                    var sid = prefs.getString(KEY_SESSION, null)
                    if (sid == null) {
                        sid = repository.start()
                        prefs.edit().putString(KEY_SESSION, sid).commit()
                        broadcast(STATE_STARTED)
                    }
                    if (!stopping) repository.append(sid, fix)
                    if (!stopping) broadcast(STATE_SENT)
                } catch (error: ApiClient.ApiException) {
                    when (error.statusCode) {
                        401 -> stopSharing("로그인이 만료되어 위치 공유를 종료했습니다.")
                        404 -> {
                            prefs.edit().remove(KEY_SESSION).apply()
                            broadcast(STATE_RETRY, "위치 공유 세션을 다시 시작합니다.")
                        }
                        else -> broadcast(STATE_RETRY, "네트워크 오류로 다음 위치를 재전송합니다.")
                    }
                } catch (error: Exception) {
                    broadcast(STATE_RETRY, "위치 전송에 실패했습니다. 다음 위치에서 재시도합니다.")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "GPS 위치 공유", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing(null)
            return START_NOT_STICKY
        }
        if (stopping) return START_NOT_STICKY
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().remove(KEY_SESSION).putBoolean(KEY_ACTIVE, false).apply()
            broadcast(STATE_STOPPED, "위치 권한이 없어 공유를 종료했습니다.")
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_nav_map)
            .setContentTitle("위치 공유 중")
            .setContentText("약 10초마다 현재 위치를 전송합니다.")
            .setContentIntent(PendingIntent.getActivity(this, 0,
                Intent(this, MapActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).build())
        prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
        if (requesting) return START_STICKY
        try {
            client.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                    .setMinUpdateIntervalMillis(9_000L)
                    .setMaxUpdateDelayMillis(10_000L)
                    .build(), callback, Looper.getMainLooper(),
            )
            requesting = true
            permissionWatch = scope.launch {
                while (isActive && !stopping) {
                    delay(10_000L)
                    if (ActivityCompat.checkSelfPermission(this@GpsSharingService, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this@GpsSharingService, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                    ) stopSharing("위치 권한이 없어 공유를 종료했습니다.")
                }
            }
            broadcast(STATE_STARTED)
        } catch (_: SecurityException) {
            stopSharing("위치 권한이 없어 공유를 종료했습니다.")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun stopSharing(message: String?) {
        if (stopping) return
        stopping = true
        if (requesting) client.removeLocationUpdates(callback)
        requesting = false
        permissionWatch?.cancel()
        stopJob = scope.launch {
            upload?.join()
            val sid = prefs.getString(KEY_SESSION, null)
            if (sid != null) {
                for (attempt in 0 until 3) {
                    try {
                        repository.close(sid)
                        break
                    } catch (error: ApiClient.ApiException) {
                        if (error.statusCode == 404 || error.statusCode == 401) break
                    } catch (_: Exception) { }
                    if (attempt < 2) delay((attempt + 1) * 1_000L)
                }
            }
            prefs.edit().remove(KEY_SESSION).putBoolean(KEY_ACTIVE, false).apply()
            broadcast(STATE_STOPPED, message)
            stopSelf()
        }
    }

    private fun broadcast(state: String, message: String? = null) {
        sendBroadcast(Intent(state).setPackage(packageName).putExtra(EXTRA_MESSAGE, message))
    }

    override fun onDestroy() {
        if (requesting) client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val STATE_STARTED = "com.safewalk.location.STARTED"
        const val STATE_SENT = "com.safewalk.location.SENT"
        const val STATE_RETRY = "com.safewalk.location.RETRY"
        const val STATE_STOPPED = "com.safewalk.location.STOPPED"
        const val EXTRA_MESSAGE = "message"
        private const val ACTION_START = "com.safewalk.location.START"
        private const val ACTION_STOP = "com.safewalk.location.STOP"
        private const val KEY_SESSION = "sessionId"
        private const val KEY_ACTIVE = "active"
        private const val CHANNEL = "gps_sharing"
        private const val NOTIFICATION_ID = 41002

        fun isSharing(context: Context): Boolean = context.getSharedPreferences("gps_sharing", MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GpsSharingService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GpsSharingService::class.java).setAction(ACTION_STOP))
        }
    }
}
