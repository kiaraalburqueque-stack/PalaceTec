package com.tecsup.palacetec.ui.bets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.HistoryItem
import com.tecsup.palacetec.data.repository.HistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BetsUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<HistoryItem> = emptyList(),
    val total: Long = 0,
    val hasMore: Boolean = false,
    val error: String? = null,
    /** true cuando se muestra la copia guardada en Room. */
    val fromCache: Boolean = false,
)

class BetsViewModel(private val history: HistoryRepository) : ViewModel() {

    private val _state = MutableStateFlow(BetsUiState())
    val state: StateFlow<BetsUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var page = 1

    fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true, loadingMore = false, error = null) }
        job = viewModelScope.launch {
            if (_state.value.items.isEmpty()) {
                val saved = history.cached()
                _state.update { it.copy(items = saved, fromCache = saved.isNotEmpty()) }
            }
            try {
                val first = history.refresh(PAGE_SIZE)
                page = 1
                _state.value = BetsUiState(
                    loading = false, items = first.results, total = first.count, hasMore = first.next != null,
                )
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (job?.isActive == true || !current.hasMore) return
        _state.update { it.copy(loadingMore = true, error = null) }
        job = viewModelScope.launch {
            try {
                val next = history.page(page + 1, PAGE_SIZE)
                page++
                _state.update { state ->
                    state.copy(
                        loadingMore = false,
                        // Si entró una apuesta nueva, la página se corre y repite elementos.
                        items = (state.items + next.results).distinctBy { it.source to it.id },
                        total = next.count,
                        hasMore = next.next != null,
                    )
                }
            } catch (error: ApiException) {
                // Se corta la carga automática; el botón Reintentar vuelve a empezar.
                _state.update { it.copy(loadingMore = false, hasMore = false, error = error.message) }
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
