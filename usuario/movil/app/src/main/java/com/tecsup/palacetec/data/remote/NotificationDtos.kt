package com.tecsup.palacetec.data.remote

import com.google.gson.annotations.SerializedName

data class PlayerNotification(
    val id: Long,
    /** PROMOTION, SECURITY o GAME. */
    val category: String,
    val title: String,
    val message: String,
    @SerializedName("is_read") val isRead: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class NotificationList(
    @SerializedName("unread_count") val unreadCount: Long,
    val results: List<PlayerNotification>,
)

/** Las dos operaciones de marcar devuelven cuántas quedan sin leer. */
data class UnreadCount(@SerializedName("unread_count") val unreadCount: Long)
