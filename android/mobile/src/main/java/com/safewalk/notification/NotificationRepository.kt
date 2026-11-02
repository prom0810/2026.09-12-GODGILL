package com.safewalk.notification

import android.content.Context
import com.safewalk.common.network.AuthenticatedApiClient

class NotificationRepository(context: Context) {
    private val api = AuthenticatedApiClient(context)

    fun page(page: Int, size: Int = 20): NotificationPage {
        val data = api.getObject("/api/notifications?page=$page&size=$size")
        val itemsJson = data.getJSONArray("items")
        return NotificationPage(
            items = (0 until itemsJson.length()).map { itemsJson.getJSONObject(it).toNotificationItem() },
            page = data.getInt("page"),
            hasNext = data.getBoolean("hasNext"),
            unreadCount = data.getLong("unreadCount"),
        )
    }

    fun unreadCount(): Long = api.getObject("/api/notifications/unread-count").getLong("unreadCount")

    fun markRead(id: Long): NotificationItem =
        api.patchObject("/api/notifications/$id/read").toNotificationItem()

    fun markAllRead(): Int = api.patchObject("/api/notifications/read-all").getInt("updatedCount")
}
