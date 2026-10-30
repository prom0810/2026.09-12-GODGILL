package com.safewalk.notification

import org.json.JSONObject

data class NotificationItem(
    val id: Long,
    val type: String,
    val title: String,
    val content: String,
    val referenceType: String?,
    val referenceId: Long?,
    val read: Boolean,
    val createdAt: String,
)

data class NotificationPage(
    val items: List<NotificationItem>,
    val page: Int,
    val hasNext: Boolean,
    val unreadCount: Long,
)

internal fun JSONObject.toNotificationItem() = NotificationItem(
    id = getLong("notificationId"),
    type = getString("type"),
    title = getString("title"),
    content = getString("content"),
    referenceType = optString("referenceType").takeIf { it.isNotBlank() && it != "null" },
    referenceId = if (has("referenceId") && !isNull("referenceId")) getLong("referenceId") else null,
    read = optBoolean("read"),
    createdAt = optString("createdAt"),
)
