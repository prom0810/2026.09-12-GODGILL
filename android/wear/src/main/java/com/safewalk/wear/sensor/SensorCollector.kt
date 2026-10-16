package com.safewalk.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType

/**
 * 가속도(중력 포함, m/s²) + 자이로(rad/s) + 심박수 수집.
 * 심박 권한(READ_HEART_RATE / BODY_SENSORS)은 시작 전에 확보되어 있어야 한다.
 *
 *  - onAccel : 낙상 판정용. 센서 단조 시간(ms)을 넘긴다.
 *  - onSample: 폰/서버 전송용. 가속도 이벤트마다 "최신 자이로"를 붙여 epoch(ms) 시간으로 넘긴다.
 */
class SensorCollector(
    private val context: Context,
    private val onAccel: (timeMs: Long, x: Float, y: Float, z: Float) -> Unit,
    private val onHeartRate: (timeMs: Long, bpm: Double) -> Unit,
    private val onHrStatus: (String) -> Unit,
    private val onSample: (epochMs: Long, ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) -> Unit,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val measureClient = HealthServices.getClient(context).measureClient
    private var running = false

    private var gx = 0f
    private var gy = 0f
    private var gz = 0f

    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val tMs = e.timestamp / 1_000_000                       // elapsedRealtime 기준(단조)
            onAccel(tMs, e.values[0], e.values[1], e.values[2])
            // SensorEvent.timestamp 는 부팅 후 시간 → 현재 시각 기준 epoch 로 환산
            val epochMs = System.currentTimeMillis() - SystemClock.elapsedRealtime() + tMs
            onSample(epochMs, e.values[0], e.values[1], e.values[2], gx, gy, gz)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            gx = e.values[0]; gy = e.values[1]; gz = e.values[2]
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            val a = availability.toString()
            onHrStatus(
                when {
                    a.contains("OFF_BODY") -> "미착용 감지"
                    a.contains("UNAVAILABLE") -> "사용 불가"
                    a.contains("ACQUIRING") -> "측정 준비 중"
                    a.contains("AVAILABLE") -> "측정 중"
                    else -> a
                }
            )
        }

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let {
                onHeartRate(System.currentTimeMillis(), it.value)
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(gyroListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_GAME)
        }
        try {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, hrCallback)
        } catch (_: Exception) {
            onHrStatus("심박 사용 불가")      // 심박이 실패해도 낙상 감지는 계속한다
        }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(accelListener)
        sensorManager.unregisterListener(gyroListener)
        try {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
        } catch (_: Exception) { }
    }
}
