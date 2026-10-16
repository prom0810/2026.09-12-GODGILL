package com.safewalk.watch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/** 알림에 붙일 폰의 마지막 위치. 새 라이브러리 없이 LocationManager 의 캐시를 쓴다(권한 없으면 null). */
object PhoneLocation {
    fun refresh(context: Context) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        var best: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: SecurityException) { null } catch (_: Exception) { null }
            if (l != null && (best == null || l.time > best.time)) best = l
        }
        best?.let {
            WatchState.lastLat = it.latitude
            WatchState.lastLon = it.longitude
            WatchState.lastAccuracy = it.accuracy
        }
    }
}
