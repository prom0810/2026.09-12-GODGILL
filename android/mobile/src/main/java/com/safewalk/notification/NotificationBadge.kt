package com.safewalk.notification

import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.safewalk.R
import com.safewalk.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow

object NotificationEvents {
    val incoming = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun notifyIncoming() { incoming.tryEmit(Unit) }
}

object NotificationBadge {
    fun refresh(activity: ComponentActivity) {
        val badge = activity.findViewById<TextView>(R.id.notification_badge) ?: return
        if (!TokenStore(activity).isLoggedIn()) {
            badge.visibility = View.GONE
            return
        }
        activity.lifecycleScope.launch {
            val count = runCatching {
                withContext(Dispatchers.IO) { NotificationRepository(activity).unreadCount() }
            }.getOrNull() ?: return@launch
            badge.visibility = if (count > 0) View.VISIBLE else View.GONE
            badge.text = if (count > 99) "99+" else count.toString()
        }
    }
}
