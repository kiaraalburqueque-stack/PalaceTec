package com.tecsup.palacetec.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.repository.PushRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val notificationsEnabled: Boolean = false,
    /** Herramientas de prueba: solo existen en la compilación debug. */
    val showDebugTools: Boolean = false,
    val loadingToken: Boolean = false,
    val token: String? = null,
)

class SettingsViewModel(private val push: PushRepository, private val debug: Boolean) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(showDebugTools = debug))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Al volver de los ajustes del sistema el permiso pudo cambiar. */
    fun onVisible() {
        _state.update { it.copy(notificationsEnabled = push.notificationsEnabled()) }
        if (debug && _state.value.token == null) loadToken()
    }

    private fun loadToken() {
        if (_state.value.loadingToken) return
        _state.update { it.copy(loadingToken = true) }
        viewModelScope.launch {
            val token = push.token()
            _state.update { it.copy(loadingToken = false, token = token) }
        }
    }

    /** Cierra la app a propósito para comprobar que el informe llega a Crashlytics. */
    fun forceCrash() {
        if (debug) throw IllegalStateException("Crash de prueba de PalaceTec")
    }
}

/** Decide si toca pedir el permiso de notificaciones al entrar. */
class PushPermissionViewModel(private val push: PushRepository) : ViewModel() {

    private val _ask = MutableStateFlow(false)
    val ask: StateFlow<Boolean> = _ask.asStateFlow()

    init {
        viewModelScope.launch { _ask.value = push.shouldAskPermission() }
    }

    fun onAsked() {
        _ask.value = false
        viewModelScope.launch { push.onPermissionAsked() }
    }
}
