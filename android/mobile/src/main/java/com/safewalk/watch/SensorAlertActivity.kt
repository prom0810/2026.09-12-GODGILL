package com.safewalk.watch

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.safewalk.emergency.EmergencyMapActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 워치 이상 감지 팝업 (잠금 화면 위에도 뜸). 상태는 [SensorAlertCoordinator]가 가지고, 이 화면은 보여 주기만 한다.
 * 화면 디자인은 프론트 담당이 다듬을 수 있도록 레이아웃 XML 없이 최소 구성으로 둔다.
 */
class SensorAlertActivity : ComponentActivity() {

    private lateinit var title: TextView
    private lateinit var message: TextView
    private lateinit var countdown: TextView
    private lateinit var okButton: Button
    private lateinit var helpButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildLayout())
        // 뒤로 가기로 팝업만 닫히면 카운트다운이 보이지 않은 채 SOS가 나가므로 막는다 (버튼으로만 응답)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val s = SensorAlertCoordinator.state.value
                if (s !is SensorAlertCoordinator.State.Counting && s !is SensorAlertCoordinator.State.Sending) finish()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SensorAlertCoordinator.state.collectLatest { render(it) }
            }
        }
    }

    private suspend fun render(state: SensorAlertCoordinator.State) {
        when (state) {
            is SensorAlertCoordinator.State.Idle -> finish()
            is SensorAlertCoordinator.State.Counting -> {
                title.text = "🚨 ${state.alert.title}"
                message.text = "괜찮으면 '괜찮아요'를 눌러 주세요.\n응답이 없으면 보호자에게 SOS와 위치를 보내요."
                okButton.text = "괜찮아요"
                okButton.isEnabled = true
                okButton.setOnClickListener { SensorAlertCoordinator.userOk(this) }
                helpButton.visibility = android.view.View.VISIBLE
                helpButton.setOnClickListener { SensorAlertCoordinator.userHelp(this) }
                while (true) {
                    val left = ((state.countdownEndElapsedMs - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0)
                    countdown.text = left.toString()
                    if (left == 0L) break
                    delay(250)
                }
            }
            is SensorAlertCoordinator.State.Sending -> {
                title.text = "🚨 ${state.alert.title}"
                message.text = "보호자에게 SOS를 보내는 중이에요…"
                countdown.text = ""
                okButton.isEnabled = false
                helpButton.visibility = android.view.View.GONE
            }
            is SensorAlertCoordinator.State.Started -> {
                SensorAlertCoordinator.acknowledge()
                startActivity(Intent(this, EmergencyMapActivity::class.java)
                    .putExtra(EmergencyMapActivity.EXTRA_EMERGENCY_ID, state.emergencyId))
                finish()
            }
            is SensorAlertCoordinator.State.Failed -> {
                title.text = "SOS를 보내지 못했어요"
                message.text = state.message
                countdown.text = ""
                okButton.isEnabled = true
                okButton.text = "확인"
                okButton.setOnClickListener { SensorAlertCoordinator.acknowledge() }
                helpButton.visibility = android.view.View.GONE
            }
        }
    }

    private fun buildLayout(): LinearLayout {
        val pad = (24 * resources.displayMetrics.density).toInt()
        fun text(size: Float, bold: Boolean = false) = TextView(this).apply {
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, pad / 2, 0, pad / 2)
        }
        title = text(24f, bold = true)
        countdown = text(64f, bold = true)
        message = text(16f)
        helpButton = Button(this).apply { text = "지금 도움 요청" }
        okButton = Button(this).apply { text = "괜찮아요" }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(countdown)
            addView(message)
            addView(helpButton)
            addView(okButton)
        }
    }
}
