package com.tecsup.palacetec.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.PlayerNotification
import com.tecsup.palacetec.data.repository.NotificationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val items: List<PlayerNotification> = emptyList(),
    val error: String? = null,
    val markingAll: Boolean = false,
    /** Fallo al marcar; la lista sigue visible. */
    val actionError: String? = null,
)

class NotificationsViewModel(private val repository: NotificationRepository) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    /** Puede superar lo que se ve: la lista trae las 30 últimas. */
    val unread: StateFlow<Long> = repository.unread

    private var loadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true || _state.value.markingAll) return
        _state.update { it.copy(loading = true, error = null, actionError = null) }
        loadJob = viewModelScope.launch {
            try {
                val items = repository.latest()
                _state.update { it.copy(loading = false, loaded = true, items = items) }
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    /** Se ve leída al instante; si el servidor falla, vuelve a quedar sin leer. */
    fun markRead(notification: PlayerNotification) {
        if (notification.isRead || _state.value.markingAll) return
        stopLoading()
        setRead(setOf(notification.id), true)
        viewModelScope.launch {
            try {
                repository.markRead(notification.id)
            } catch (error: ApiException) {
                setRead(setOf(notification.id), false)
                _state.update { it.copy(actionError = error.message) }
            }
        }
    }

    fun markAllRead() {
        if (_state.value.markingAll) return
        stopLoading()
        val pending = _state.value.items.filter { !it.isRead }.map { it.id }.toSet()
        setRead(pending, true)
        _state.update { it.copy(markingAll = true) }
        viewModelScope.launch {
            try {
                repository.markAllRead()
                _state.update { it.copy(markingAll = false) }
            } catch (error: ApiException) {
                setRead(pending, false)
                _state.update { it.copy(markingAll = false, actionError = error.message) }
            }
        }
    }

    /** Una carga pedida antes de marcar traería el estado anterior. */
    private fun stopLoading() {
        loadJob?.cancel()
        _state.update { it.copy(loading = false, actionError = null) }
    }

    private fun setRead(ids: Set<Long>, read: Boolean) = _state.update { state ->
        state.copy(items = state.items.map { if (it.id in ids) it.copy(isRead = read) else it })
    }
}

/** Contador de la campana. */
class UnreadViewModel(private val repository: NotificationRepository) : ViewModel() {

    val unread: StateFlow<Long> = repository.unread

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                repository.latest()
            } catch (_: ApiException) {
                // La campana conserva su último valor; el centro muestra el error al abrirlo.
            }
        }
    }
}
