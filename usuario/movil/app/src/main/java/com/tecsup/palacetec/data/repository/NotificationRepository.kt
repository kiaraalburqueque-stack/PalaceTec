package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.PlayerNotification
import com.tecsup.palacetec.data.remote.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** El contador es común a la campana y al centro de notificaciones. */
class NotificationRepository(private val api: PalaceTecApi) {

    private val _unread = MutableStateFlow(0L)
    val unread: StateFlow<Long> = _unread.asStateFlow()

    suspend fun latest(): List<PlayerNotification> {
        val list = apiCall { api.notifications() }
        _unread.value = list.unreadCount
        return list.results
    }

    suspend fun markRead(id: Long) {
        _unread.value = apiCall { api.markNotificationRead(id) }.unreadCount
    }

    suspend fun markAllRead() {
        _unread.value = apiCall { api.markAllNotificationsRead() }.unreadCount
    }

    /** Al cerrar sesión: el siguiente jugador no hereda el contador. */
    fun reset() {
        _unread.value = 0
    }
}
