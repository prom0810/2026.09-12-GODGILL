package com.safewalk.wear.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 센서 CSV 녹화 도구 (임계값 튜닝용 데이터 수집).
 * 저장: <앱 외부 files 폴더>/rec_yyyyMMdd_HHmmss.csv
 *   열: time_ms,label,ax,ay,az,gx,gy,gz,bpm
 *   (가속도 m/s² 중력 포함 약 50Hz 기준 행, 자이로 rad/s 는 가장 최근 값)
 * 라벨은 녹화 중에도 바꿀 수 있다 (행마다 현재 라벨 기록).
 */
class RecorderActivity : ComponentActivity(), SensorEventListener {

    private val labels = listOf("걷기", "뛰기", "앉기", "서있기", "낙상(모의)", "계단")
    private val labelCodes = listOf("walk", "run", "sit", "stand", "fall_sim", "stairs")

    private lateinit var sensorManager: SensorManager
    private lateinit var measureClient: MeasureClient
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    @Volatile private var gx = 0f
    @Volatile private var gy = 0f
    @Volatile private var gz = 0f
    private var writer: BufferedWriter? = null
    private var rows = 0L
    @Volatile private var latestBpm: Double? = null
    private var hrRegistered = false

    private var recording by mutableStateOf(false)
    private var rowCount by mutableStateOf(0L)
    private var labelIndex by mutableStateOf(0)
    private var bpmText by mutableStateOf<Double?>(null)
    private var startedAt by mutableStateOf(0L)
    private var savedInfo by mutableStateOf("")
    private var hrStatus by mutableStateOf("대기")

    private val heartRatePermission: String =
        if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
        else Manifest.permission.BODY_SENSORS

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            val a = availability.toString()
            hrStatus = when {
                a.contains("OFF_BODY") -> "미착용 감지"
                a.contains("UNAVAILABLE") -> "사용 불가"
                a.contains("ACQUIRING") -> "측정 준비 중"
                a.contains("AVAILABLE") -> "측정 중"
                else -> a
            }
            if (a.contains("UNAVAILABLE")) { latestBpm = null; bpmText = null }
        }
        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let {
                latestBpm = it.value
                bpmText = it.value
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startHeartRate() else hrStatus = "권한 거부됨" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        measureClient = HealthServices.getClient(this).measureClient

        setContent {
            MaterialTheme {
                var elapsedSec by remember { mutableStateOf(0L) }
                LaunchedEffect(recording) {
                    while (recording) {
                        elapsedSec = (System.currentTimeMillis() - startedAt) / 1000
                        delay(500)
                    }
                }
                RecorderScreen(elapsedSec)
            }
        }

        if (ContextCompat.checkSelfPermission(this, heartRatePermission) != PackageManager.PERMISSION_GRANTED) {
            hrStatus = "권한 필요"
            permissionLauncher.launch(heartRatePermission)
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, heartRatePermission) == PackageManager.PERMISSION_GRANTED) startHeartRate()
    }

    override fun onPause() {
        // 녹화 중이 아니면 심박 센서 해제 (재진입 시 onResume 에서 다시 등록)
        if (!recording) stopHeartRate()
        super.onPause()
    }

    @androidx.compose.runtime.Composable
    private fun RecorderScreen(elapsedSec: Long) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "♥ ${bpmText?.let { "%.0f".format(it) } ?: "--"} bpm · $hrStatus",
                fontSize = 12.sp, color = Color(0xFFFF8A80),
            )
            Spacer(Modifier.height(4.dp))
            // 라벨 선택 (탭하면 다음 라벨)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF263238))
                    .clickable { labelIndex = (labelIndex + 1) % labels.size }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("라벨: ${labels[labelIndex]} ▶", fontSize = 13.sp, color = Color.White) }
            Spacer(Modifier.height(8.dp))
            // 시작/정지
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (recording) Color(0xFF616161) else Color(0xFFE53935))
                    .clickable { if (recording) stopRecording() else startRecording() }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    if (recording) "■ 정지" else "● 녹화 시작",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (recording) "녹화 중 ${elapsedSec / 60}:${"%02d".format(elapsedSec % 60)} · ${rowCount}행"
                else savedInfo.ifEmpty { "라벨 선택 후 시작" },
                fontSize = 10.sp, color = Color(0xFFBDBDBD), textAlign = TextAlign.Center,
            )
        }
    }

    private fun startRecording() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "rec_$ts.csv")
        writer = BufferedWriter(FileWriter(file), 64 * 1024).also { it.write("time_ms,label,ax,ay,az,gx,gy,gz,bpm\n") }
        savedInfo = file.absolutePath
        rows = 0; rowCount = 0
        startedAt = System.currentTimeMillis()
        gx = 0f; gy = 0f; gz = 0f
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recording = true
    }

    private fun stopRecording() {
        recording = false
        sensorManager.unregisterListener(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching { writer?.flush(); writer?.close() }
        writer = null
        rowCount = rows
        savedInfo = "저장됨 ${rows}행\n${File(savedInfo).name}"
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_GYROSCOPE) {
            gx = e.values[0]; gy = e.values[1]; gz = e.values[2]
            return
        }
        val w = writer ?: return
        val bpm = latestBpm?.let { "%.0f".format(it) } ?: ""
        w.write("${System.currentTimeMillis()},${labelCodes[labelIndex]},${e.values[0]},${e.values[1]},${e.values[2]},$gx,$gy,$gz,$bpm\n")
        rows++
        if (rows % 25 == 0L) rowCount = rows
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startHeartRate() {
        if (hrRegistered) return
        hrRegistered = true
        hrStatus = "측정 준비 중"
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, hrCallback)
    }

    private fun stopHeartRate() {
        if (!hrRegistered) return
        hrRegistered = false
        measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
    }

    override fun onDestroy() {
        if (recording) stopRecording()
        stopHeartRate()
        super.onDestroy()
    }
}
