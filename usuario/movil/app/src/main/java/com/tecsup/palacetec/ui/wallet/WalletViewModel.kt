package com.tecsup.palacetec.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.WalletSummary
import com.tecsup.palacetec.data.repository.WalletRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletUiState(
    val loading: Boolean = true,
    val wallet: WalletSummary? = null,
    val error: String? = null,
)

class WalletViewModel(private val repository: WalletRepository) : ViewModel() {

    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val wallet = repository.summary()
                _state.update { it.copy(loading = false, wallet = wallet) }
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }
}
