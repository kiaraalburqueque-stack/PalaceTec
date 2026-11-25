package com.tecsup.palacetec.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.BalanceHistory
import com.tecsup.palacetec.data.remote.BiggestWin
import com.tecsup.palacetec.data.remote.TopGames
import com.tecsup.palacetec.data.remote.WinRate
import com.tecsup.palacetec.data.repository.StatsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class StatsUiState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val error: String? = null,
    val winRate: WinRate? = null,
    val biggestWin: BiggestWin? = null,
    val topGames: TopGames? = null,
    /** 7 o 30 días. */
    val period: Int = 7,
    val balance: BalanceHistory? = null,
    val chart: BalanceChart? = null,
    /** Día que se está leyendo en el gráfico. */
    val selectedPoint: Int = 0,
    val loadingBalance: Boolean = false,
    val balanceError: String? = null,
)

class StatsViewModel(private val repository: StatsRepository) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var balanceJob: Job? = null

    fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(loading = true, error = null) }
        loadBalance()
        loadJob = viewModelScope.launch {
            try {
                // Las tres consultas van en paralelo.
                coroutineScope {
                    val winRate = async { repository.winRate() }
                    val biggestWin = async { repository.biggestWin() }
                    val topGames = async { repository.topGames() }
                    val loaded = Triple(winRate.await(), biggestWin.await(), topGames.await())
                    _state.update {
                        it.copy(
                            loading = false, loaded = true, winRate = loaded.first, biggestWin = loaded.second,
                            topGames = loaded.third,
                        )
                    }
                }
            } catch (error: ApiException) {
                _state.update { it.copy(loading = false, error = error.message) }
            }
        }
    }

    fun selectPeriod(days: Int) {
        if (days == _state.value.period) return
        _state.update { it.copy(period = days) }
        loadBalance()
    }

    /** Cancela la consulta anterior: solo vale la del último plazo elegido. */
    fun loadBalance() {
        balanceJob?.cancel()
        _state.update { it.copy(loadingBalance = true, balanceError = null) }
        balanceJob = viewModelScope.launch {
            try {
                val balance = repository.balanceHistory(_state.value.period)
                _state.update {
                    it.copy(
                        loadingBalance = false, balance = balance, chart = balanceChart(balance.points),
                        selectedPoint = balance.points.lastIndex.coerceAtLeast(0),
                    )
                }
            } catch (error: ApiException) {
                _state.update { it.copy(loadingBalance = false, balanceError = error.message) }
            }
        }
    }

    /** position va de 0 (izquierda) a 1 (derecha): se lee el día más cercano. */
    fun onChartTouch(position: Float) = _state.update { state ->
        val last = state.balance?.points?.lastIndex ?: return@update state
        state.copy(selectedPoint = (position * last).roundToInt().coerceIn(0, last.coerceAtLeast(0)))
    }

    companion object {
        /** Los plazos que acepta el backend. */
        val PERIODS = listOf(7, 30)
    }
}
