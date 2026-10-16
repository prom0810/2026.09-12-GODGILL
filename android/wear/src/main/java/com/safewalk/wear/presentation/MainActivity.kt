package com.safewalk.wear.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.safewalk.wear.log.EventLog
import com.safewalk.wear.sensor.MonitoringService
import com.safewalk.wear.settings.Settings
import com.safewalk.wear.sos.EventType
import com.safewalk.wear.sos.SafetyState
import com.safewalk.wear.sos.SosManager
import com.safewalk.wear.sync.PhoneAlertSink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SosRed = Color(0xFFE53935)
private val SosRedDark = Color(0xFFB71C1C)
private val RingGray = Color(0xFFF2F2F2)
private val Muted = Color(0xFFBDBDBD)
private val Accent = Color(0xFF90CAF9)
private const val HOLD_MS = 3000L

private enum class Screen { MAIN, HISTORY, SETTINGS }

/**
 * 워치 단독 안전 앱.
 *  - 실행하면 자동으로 감시(낙상/심박) 시작, 폰 없이 동작
 *  - 낙상/심박 이상/수동 SOS -> 취소 가능한 카운트다운 -> 경보(소리+진동) + 112 직접 걸기 버튼
 *  - 폰이 연결돼 있으면 이벤트/센서 데이터를 폰으로 전송(설정에서 켬)
 */
class MainActivity : ComponentActivity() {

    private val hrPermission =
        if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE"
        else Manifest.permission.BODY_SENSORS

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startMonitoringIfAllowed() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        EventLog.init(this)
        PhoneAlertSink.install()
        setShowWhenLocked(true)      // 낙상 경보가 잠금/꺼진 화면 위에 뜨도록
        setTurnScreenOn(true)

        setContent {
            MaterialTheme {
                var screen by remember { mutableStateOf(Screen.MAIN) }
                val urgent = SafetyState.countdown > 0 || SafetyState.emergencyActive

                // 카운트다운/긴급 중에는 화면이 꺼지지 않게 유지
                SideEffect {
                    if (urgent) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                BackHandler(enabled = screen != Screen.MAIN && !urgent) { screen = Screen.MAIN }

                when {
                    SafetyState.emergencyActive -> EmergencyScreen()
                    SafetyState.countdown > 0 -> CountdownScreen()
                    screen == Screen.HISTORY -> HistoryScreen(onBack = { screen = Screen.MAIN })
                    screen == Screen.SETTINGS -> SettingsScreen(
                        onBack = { screen = Screen.MAIN },
                        onOpenRecorder = {
                            startActivity(Intent(this@MainActivity, RecorderActivity::class.java))
                        },
                    )
                    else -> MainScreen(
                        onHistory = { screen = Screen.HISTORY },
                        onSettings = { screen = Screen.SETTINGS },
                        onToggleMonitoring = {
                            if (SafetyState.monitoring) stopMonitoring() else ensurePermissionsAndStart()
                        },
                    )
                }
            }
        }
        ensurePermissionsAndStart()
    }

    private fun ensurePermissionsAndStart() {
        val needed = buildList {
            add(hrPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) startMonitoringIfAllowed() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startMonitoringIfAllowed() {
        // health 타입 포그라운드 서비스는 심박 권한이 있어야 시작할 수 있다
        if (ContextCompat.checkSelfPermission(this, hrPermission) == PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(this, Intent(this, MonitoringService::class.java))
        } else SafetyState.lastMessage = "심박 권한이 필요합니다"
    }

    private fun stopMonitoring() { stopService(Intent(this, MonitoringService::class.java)) }
}

// ───────────────────────── 메인 ─────────────────────────

@Composable
private fun MainScreen(onHistory: () -> Unit, onSettings: () -> Unit, onToggleMonitoring: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("도움이 필요하신가요?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)

        // 바깥 연한 링 + 빨간 SOS 버튼 (3초 길게 누르면 진행 링이 채워짐)
        Box(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .size(124.dp)
                .clip(CircleShape)
                .background(RingGray)
                .drawBehind {
                    drawArc(
                        color = SosRedDark, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                        style = Stroke(width = 6.dp.toPx()),
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val job = scope.launch {
                            val t0 = System.currentTimeMillis()
                            while (true) {
                                val p = ((System.currentTimeMillis() - t0) / HOLD_MS.toFloat()).coerceAtMost(1f)
                                progress = p
                                if (p >= 1f) {
                                    SosManager.start(context, EventType.SOS_MANUAL, "SOS 요청", auto = false)
                                    break
                                }
                                delay(16)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        progress = 0f
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(SosRed),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SOS", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("3초간 길게 누르기", fontSize = 9.sp, color = Color.White)
                }
            }
        }

        val bpm = SafetyState.bpm?.let { "%.0f".format(it) } ?: "--"
        Text("♥ $bpm bpm · ${SafetyState.hrStatus}", fontSize = 11.sp, color = Color.White)
        Text(
            text = SafetyState.lastMessage.ifEmpty { "${SosManager.MANUAL_SECONDS}초 취소 시간 후 경보가 울립니다" },
            fontSize = 10.sp, color = Muted, textAlign = TextAlign.Center,
        )
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("기록", fontSize = 11.sp, color = Accent, modifier = Modifier.clickable { onHistory() }.padding(4.dp))
            Text("설정", fontSize = 11.sp, color = Accent, modifier = Modifier.clickable { onSettings() }.padding(4.dp))
        }
        Text(
            text = if (SafetyState.monitoring) "감시 중 · 중지" else "감시 꺼짐 · 시작",
            fontSize = 10.sp, color = Accent,
            modifier = Modifier.clickable { onToggleMonitoring() }.padding(4.dp),
        )
    }
}

// ───────────────────────── 카운트다운 / 긴급 ─────────────────────────

@Composable
private fun CountdownScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(SosRed).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(SafetyState.countdownReason, fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center)
        Text("${SafetyState.countdown}", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text("초 후 경보", fontSize = 11.sp, color = Color.White)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { SosManager.cancel(context) }
                .padding(horizontal = 22.dp, vertical = 10.dp),
        ) { Text("괜찮아요 (취소)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = SosRedDark) }
    }
}

@Composable
private fun EmergencyScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(SosRedDark).verticalScroll(rememberScrollState()).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("긴급 상황", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Text(SafetyState.emergencyReason, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center)
        Text(formatTime(SafetyState.emergencyTimeMs), fontSize = 10.sp, color = Muted)
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { dial112(context) }
                .padding(horizontal = 24.dp, vertical = 10.dp),
        ) { Text("112 전화", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = SosRedDark) }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable { SosManager.dismissEmergency(context) }
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) { Text("경보 해제", fontSize = 12.sp, color = Color.White) }
        if (SafetyState.lastMessage.isNotEmpty()) {
            Text(SafetyState.lastMessage, fontSize = 10.sp, color = Muted, textAlign = TextAlign.Center)
        }
    }
}

/** 자동으로 걸지 않고 전화 화면만 띄운다. 통화 기능이 없는 기기(LTE 없음)는 메시지로 안내. */
private fun dial112(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        SafetyState.lastMessage = "이 기기에서는 전화를 걸 수 없습니다"
    }
}

// ───────────────────────── 기록 ─────────────────────────

@Composable
private fun HistoryScreen(onBack: () -> Unit) {
    val list = EventLog.recent.filter { it.type != EventType.TEST }
    val cancelled = list.count { it.outcome == "CANCELLED" }
    val confirmed = list.count { it.outcome == "CONFIRMED" }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("기록", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("취소 $cancelled · 경보 $confirmed", fontSize = 10.sp, color = Muted)
        if (list.isEmpty()) {
            Text("아직 기록이 없습니다", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 10.dp))
        }
        list.take(20).forEach { r ->
            val outcome = if (r.outcome == "CONFIRMED") "경보" else "취소"
            val bpm = r.bpm?.let { " · ♥${"%.0f".format(it)}" } ?: ""
            Text(
                "${formatTime(r.timeMs)}\n${EventType.label(r.type)} · $outcome$bpm",
                fontSize = 10.sp,
                color = if (r.outcome == "CONFIRMED") Color(0xFFFF8A80) else Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text("← 돌아가기", fontSize = 11.sp, color = Accent, modifier = Modifier.padding(top = 12.dp).clickable { onBack() }.padding(6.dp))
    }
}

// ───────────────────────── 설정 ─────────────────────────

@Composable
private fun SettingsScreen(onBack: () -> Unit, onOpenRecorder: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("설정", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Label("낙상 감도")
        Row {
            Settings.sensitivityLabels.forEachIndexed { i, name ->
                Chip(name, Settings.sensitivity == i) { Settings.updateSensitivity(i) }
            }
        }

        Label("자동 감지 후 취소 시간")
        Row {
            listOf(10, 15, 20).forEach { sec ->
                Chip("${sec}초", Settings.autoSeconds == sec) { Settings.updateAutoSeconds(sec) }
            }
        }

        Label("심박 이상 알림")
        Row {
            Chip("켜짐", Settings.vitalsAlert) { Settings.updateVitalsAlert(true) }
            Chip("꺼짐", !Settings.vitalsAlert) { Settings.updateVitalsAlert(false) }
        }

        Label("경보음")
        Row {
            Chip("켜짐", Settings.sound) { Settings.updateSound(true) }
            Chip("꺼짐", !Settings.sound) { Settings.updateSound(false) }
        }

        Label("폰으로 데이터 전송")
        Row {
            Chip("켜짐", Settings.streaming) { Settings.updateStreaming(true) }
            Chip("꺼짐", !Settings.streaming) { Settings.updateStreaming(false) }
        }
        Text("켜면 배터리를 더 씁니다", fontSize = 9.sp, color = Muted)

        Box(
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(Color(0xFF3A3A3A))
                .clickable { SosManager.start(context, EventType.TEST, "테스트 알림", auto = true, seconds = 5) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Text("낙상 알림 시험", fontSize = 11.sp, color = Color.White) }

        Box(
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(Color(0xFF3A3A3A))
                .clickable { onOpenRecorder() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Text("데이터 수집(CSV 녹화)", fontSize = 11.sp, color = Color.White) }

        Text("← 돌아가기", fontSize = 11.sp, color = Accent, modifier = Modifier.padding(top = 12.dp).clickable { onBack() }.padding(6.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF1E88E5) else Color(0xFF3A3A3A))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 11.sp, color = Color.White) }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date(ms))
