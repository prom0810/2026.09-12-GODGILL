package com.safewalk.notification.view

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.safewalk.R
import com.safewalk.auth.TokenStore
import com.safewalk.common.network.ApiClient
import com.safewalk.guardian.GuardianActivity
import com.safewalk.login.LoginActivity
import com.safewalk.main.navigation.BaseMainTabActivity
import com.safewalk.main.navigation.MainTab
import com.safewalk.notification.NotificationItem
import com.safewalk.notification.NotificationRepository
import com.safewalk.notification.NotificationEvents
import com.safewalk.notification.NotificationBadge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotificationActivity : BaseMainTabActivity() {
    override val layoutResource: Int = R.layout.activity_notification_inbox
    override val selectedTab: MainTab = MainTab.NOTIFICATIONS

    private lateinit var repository: NotificationRepository
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var loadMore: Button
    private var nextPage = 0
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = NotificationRepository(this)
        list = findViewById(R.id.notification_list)
        status = findViewById(R.id.notification_status)
        loadMore = findViewById(R.id.notification_load_more)
        loadMore.setOnClickListener { loadPage(reset = false) }
        findViewById<Button>(R.id.notification_read_all).setOnClickListener { markAllRead() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationEvents.incoming.collect { loadPage(reset = true) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        if (loading) return
        if (!TokenStore(this).isLoggedIn()) {
            status.text = "로그인하면 보호자 연결 및 안전 알림을 확인할 수 있어요."
            list.removeAllViews()
            loadMore.visibility = View.GONE
            return
        }
        if (reset) {
            nextPage = 0
            list.removeAllViews()
        }
        loading = true
        status.text = "알림을 불러오는 중…"
        lifecycleScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { repository.page(nextPage) }
                page.items.forEach { list.addView(notificationCard(it)) }
                nextPage = page.page + 1
                loadMore.visibility = if (page.hasNext) View.VISIBLE else View.GONE
                status.text = when {
                    list.childCount == 0 -> "새 알림이 없습니다."
                    page.unreadCount > 0 -> "읽지 않은 알림 ${page.unreadCount}개"
                    else -> "모든 알림을 확인했습니다."
                }
                NotificationBadge.refresh(this@NotificationActivity)
            } catch (error: Exception) {
                handleError(error)
            } finally {
                loading = false
            }
        }
    }

    private fun markAllRead() {
        if (!TokenStore(this).isLoggedIn() || loading) return
        loading = true
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { repository.markAllRead() }
                Toast.makeText(this@NotificationActivity, "알림 ${count}개를 읽음 처리했습니다.", Toast.LENGTH_SHORT).show()
                loading = false
                loadPage(reset = true)
            } catch (error: Exception) {
                loading = false
                handleError(error)
            }
        }
    }

    private fun open(item: NotificationItem) {
        lifecycleScope.launch {
            try {
                if (!item.read) withContext(Dispatchers.IO) { repository.markRead(item.id) }
                when (item.referenceType) {
                    "GUARDIAN" -> startActivity(Intent(this@NotificationActivity, GuardianActivity::class.java))
                    else -> loadPage(reset = true)
                }
            } catch (error: Exception) {
                handleError(error)
            }
        }
    }

    private fun notificationCard(item: NotificationItem) = TextView(this).apply {
        val marker = if (item.read) "" else "● "
        text = "$marker${item.title}\n${item.content}\n\n${formatTime(item.createdAt)}"
        textSize = 14f
        setTextColor(Color.parseColor(if (item.read) "#737A77" else "#343A37"))
        setBackgroundResource(R.drawable.map_panel_background)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        setOnClickListener { open(item) }
    }

    private fun handleError(error: Exception) {
        if (error is ApiClient.ApiException && error.statusCode == 401) {
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            return
        }
        status.text = error.message ?: "알림을 불러오지 못했습니다."
    }

    private fun formatTime(raw: String): String = raw
        .replace('T', ' ')
        .take(16)
        .ifBlank { "시간 정보 없음" }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
