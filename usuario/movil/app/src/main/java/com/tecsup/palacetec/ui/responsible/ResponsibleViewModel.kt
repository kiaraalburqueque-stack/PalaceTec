package com.tecsup.palacetec.ui.responsible

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.DailyLimit
import com.tecsup.palacetec.data.remote.RiskAlert
import com.tecsup.palacetec.data.remote.SessionSummary
import com.tecsup.palacetec.data.repository.AuthRepository
import com.tecsup.palacetec.data.repository.DailyLimitRepository
import com.tecsup.palacetec.data.repository.ResponsibleRepository
import com.tecsup.palacetec.ui.components.isoMillis
import com.tecsup.palacetec.ui.components.limaDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResponsibleUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val error: String? = null,
    val limit: DailyLimit? = null,
    val summary: SessionSummary? = null,
    /** Fin de la pausa en curso, o null si no hay ninguna. */
    val pauseUntil: String? = null,
    /** Alerta más reciente cuya pausa todavía se puede aceptar. */
    val pauseOffer: RiskAlert? = null,
    val pausing: Boolean = false,
    val pauseError: String? = null,
    val showLimitDialog: Boolean = false,
    val savingLimit: Boolean = false,
    val limitError: String? = null,
    /** Plazo elegido que falta confirmar. */
    val exclusionDays: Int? = null,
    val excluding: Boolean = false,
    val exclusionError: String? = null,
)

class ResponsibleViewModel(
    private val dailyLimit: DailyLimitRepository,
    private val responsible: ResponsibleRepository,
    private val auth: AuthRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(ResponsibleUiState())
    val state: StateFlow<ResponsibleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                // Las tres consultas van en paralelo.
                coroutineScope {
                    val limit = async { dailyLimit.get() }
                    val summary = async { responsible.sessionSummary() }
                    val alerts = async { responsible.riskAlerts() }
                    val loaded = Triple(limit.await(), summary.await(), alerts.await())
                    _state.update {
                        it.copy(
                            loading = false, loaded = true, limit = loaded.first, summary = loaded.second,
                            pauseUntil = loaded.third.pauseUntil.ifFuture(),
                            pauseOffer = loaded.third.results.firstOrNull { alert -> alert.pauseStartedAt == null },
                        )
                    }
                }
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    private fun String?.ifFuture(): String? = this?.takeIf { (isoMillis(it) ?: 0) > now() }

    // --- Límite diario ---

    fun openLimitDialog() = _state.update { it.copy(showLimitDialog = true, limitError = null) }

    fun closeLimitDialog() = _state.update { it.copy(showLimitDialog = false) }

    fun saveLimit(amount: String) {
        if (_state.value.savingLimit) return
        _state.update { it.copy(savingLimit = true, limitError = null) }
        viewModelScope.launch {
            try {
                val limit = dailyLimit.set(amount)
                _state.update { it.copy(savingLimit = false, showLimitDialog = false, limit = limit) }
            } catch (error: ApiException) {
                _state.update { it.copy(savingLimit = false, limitError = error.message) }
            }
        }
    }

    // --- Pausa ---

    /** El backend solo acepta la pausa que sugirió una alerta de riesgo. */
    fun pause() {
        val offer = _state.value.pauseOffer ?: return
        if (_state.value.pausing) return
        _state.update { it.copy(pausing = true, pauseError = null) }
        viewModelScope.launch {
            try {
                val pause = responsible.pause(offer.incidentId)
                _state.update {
                    it.copy(pausing = false, pauseOffer = null, pauseUntil = pause.pauseUntil.ifFuture())
                }
                load()
            } catch (error: ApiException) {
                _state.update { it.copy(pausing = false, pauseError = error.message) }
            }
        }
    }

    // --- Autoexclusión ---

    fun askExclusion(days: Int) = _state.update { it.copy(exclusionDays = days, exclusionError = null) }

    fun cancelExclusion() {
        if (!_state.value.excluding) _state.update { it.copy(exclusionDays = null) }
    }

    /** El backend cierra todas las sesiones: la app vuelve al acceso con el motivo. */
    fun confirmExclusion() {
        val days = _state.value.exclusionDays ?: return
        if (_state.value.excluding) return
        _state.update { it.copy(excluding = true, exclusionError = null) }
        viewModelScope.launch {
            try {
                val exclusion = responsible.selfExclude(days)
                auth.endSession(
                    "Autoexclusión confirmada: no podrás entrar hasta el ${limaDateTime(exclusion.until)}.",
                )
            } catch (error: ApiException) {
                _state.update { it.copy(excluding = false, exclusionError = error.message) }
            }
        }
    }

    companion object {
        /** Los plazos que acepta el backend. */
        val EXCLUSION_DAYS = listOf(7, 30, 90)
    }
}
