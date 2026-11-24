package com.tecsup.palacetec.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.DailyLimit
import com.tecsup.palacetec.data.remote.Game
import com.tecsup.palacetec.data.remote.WalletSummary
import com.tecsup.palacetec.data.repository.CatalogRepository
import com.tecsup.palacetec.data.repository.DailyLimitRepository
import com.tecsup.palacetec.data.repository.WalletRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val wallet: WalletSummary? = null,
    val games: List<Game> = emptyList(),
    val dailyLimit: DailyLimit? = null,
    val error: String? = null,
    val showLimitDialog: Boolean = false,
    val savingLimit: Boolean = false,
    val limitError: String? = null,
)

class HomeViewModel(
    private val walletRepository: WalletRepository,
    private val catalogRepository: CatalogRepository,
    private val dailyLimitRepository: DailyLimitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                // Las tres consultas van en paralelo.
                coroutineScope {
                    val wallet = async { walletRepository.summary() }
                    val games = async { catalogRepository.games() }
                    val limit = async { dailyLimitRepository.get() }
                    val loaded = Triple(wallet.await(), games.await(), limit.await())
                    _state.update {
                        it.copy(
                            loading = false, wallet = loaded.first, games = loaded.second, dailyLimit = loaded.third,
                        )
                    }
                }
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun openLimitDialog() = _state.update { it.copy(showLimitDialog = true, limitError = null) }

    fun closeLimitDialog() = _state.update { it.copy(showLimitDialog = false) }

    fun saveLimit(amount: String) {
        if (_state.value.savingLimit) return
        _state.update { it.copy(savingLimit = true, limitError = null) }
        viewModelScope.launch {
            try {
                val limit = dailyLimitRepository.set(amount)
                _state.update { it.copy(savingLimit = false, showLimitDialog = false, dailyLimit = limit) }
            } catch (error: ApiException) {
                _state.update { it.copy(savingLimit = false, limitError = error.message) }
            }
        }
    }
}
