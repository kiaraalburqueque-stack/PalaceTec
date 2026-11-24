package com.tecsup.palacetec.ui.roulette

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.palacetec.data.remote.ApiException
import com.tecsup.palacetec.data.remote.BetKind
import com.tecsup.palacetec.data.remote.BetRequest
import com.tecsup.palacetec.data.remote.BetResponse
import com.tecsup.palacetec.data.remote.RoomState
import com.tecsup.palacetec.data.remote.RoundNumber
import com.tecsup.palacetec.data.remote.RouletteRoom
import com.tecsup.palacetec.data.remote.WalletSummary
import com.tecsup.palacetec.data.repository.DailyLimitRepository
import com.tecsup.palacetec.data.repository.HistoryRepository
import com.tecsup.palacetec.data.repository.RouletteRepository
import com.tecsup.palacetec.data.repository.WalletRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

/** Resultado de la ronda; staked y payout solo existen si el jugador apostó en ella. */
data class RoundResult(
    val roundId: Long,
    val number: Int,
    val color: String,
    val staked: BigDecimal? = null,
    val payout: BigDecimal? = null,
)

data class RouletteUiState(
    val loadingRooms: Boolean = true,
    val rooms: List<RouletteRoom> = emptyList(),
    val roomsError: String? = null,
    val room: RouletteRoom? = null,
    val table: RoomState? = null,
    val tableError: String? = null,
    val wallet: WalletSummary? = null,
    val result: RoundResult? = null,
    val kind: BetKind = BetKind.STRAIGHT_UP,
    val selection: String? = null,
    val chips: List<Int> = emptyList(),
    val roundBets: List<BetResponse> = emptyList(),
    val placing: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val showLimitDialog: Boolean = false,
    val savingLimit: Boolean = false,
    val limitError: String? = null,
) {
    val total: Int get() = chips.sum()

    /** Motivo por el que todavía no se puede apostar, o null si está todo listo. */
    val blockedReason: String?
        get() {
            val table = table ?: return "Conectando con la mesa…"
            val round = table.round
            if (tableError != null) return "Sin conexión con la mesa"
            if (round == null || !round.acceptsBets) return "Espera la próxima ronda"
            if (selection == null) return "Elige tu jugada"
            if (chips.isEmpty()) return "Agrega fichas"
            val amount = BigDecimal(total)
            val placed = roundBets.filter { it.roundId == round.id }.sumOf { it.amount.toBigDecimal() }
            val balance = wallet?.balance?.toBigDecimalOrNull()
            return when {
                amount < table.minBet.toBigDecimal() -> "La apuesta mínima es ${table.minBet}"
                placed + amount > table.maxBet.toBigDecimal() -> "El máximo por ronda es ${table.maxBet}"
                balance != null && amount > balance -> "Saldo insuficiente"
                else -> null
            }
        }
}

class RouletteViewModel(
    private val roulette: RouletteRepository,
    private val walletRepository: WalletRepository,
    private val history: HistoryRepository,
    private val dailyLimit: DailyLimitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RouletteUiState())
    val state: StateFlow<RouletteUiState> = _state.asStateFlow()

    private var roomsJob: Job? = null
    private var pollJob: Job? = null

    /** Rondas con apuestas mías que aún no se muestran liquidadas. */
    private val pendingRounds = mutableSetOf<Long>()

    /** Apuesta enviada sin respuesta: se reintenta con la misma referencia para no pagar dos veces. */
    private var unconfirmed: BetRequest? = null

    fun onVisible() {
        refreshWallet()
        if (_state.value.room == null) loadRooms() else startPolling()
    }

    fun onHidden() {
        pollJob?.cancel()
        pollJob = null
    }

    fun loadRooms() {
        if (roomsJob?.isActive == true) return
        _state.update { it.copy(loadingRooms = true, roomsError = null) }
        roomsJob = viewModelScope.launch {
            try {
                val rooms = roulette.rooms()
                _state.update { it.copy(loadingRooms = false, rooms = rooms) }
            } catch (error: ApiException) {
                _state.update { it.copy(loadingRooms = false, roomsError = error.message) }
            }
        }
    }

    fun selectRoom(room: RouletteRoom) {
        clearTable()
        _state.update { it.copy(room = room) }
        startPolling()
    }

    fun leaveRoom() {
        onHidden()
        clearTable()
        loadRooms()
    }

    private fun clearTable() {
        pendingRounds.clear()
        unconfirmed = null
        _state.update {
            it.copy(
                room = null, table = null, tableError = null, result = null, selection = null,
                chips = emptyList(), roundBets = emptyList(), message = null,
            )
        }
    }

    /** Consulta la mesa cada segundo. La pantalla lo detiene al dejar de verse. */
    private fun startPolling() {
        val room = _state.value.room ?: return
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    onTable(roulette.state(room.id))
                } catch (error: ApiException) {
                    _state.update { it.copy(tableError = error.message) }
                }
                delay(POLL_MILLIS)
            }
        }
    }

    private fun onTable(table: RoomState) {
        val previous = _state.value.table?.recentResults?.firstOrNull()
        val newest = table.recentResults.firstOrNull()
        _state.update { current ->
            current.copy(
                table = table,
                tableError = null,
                roundBets = current.roundBets.filter { it.roundId == table.round?.id },
            )
        }
        if (newest == null) return
        val mine = pendingRounds.filter { it <= newest.roundId }
        val isNew = previous != null && previous.roundId != newest.roundId
        if (isNew || mine.isNotEmpty()) {
            pendingRounds.removeAll(mine.toSet())
            showResult(table.roomId, newest, mine)
        }
    }

    private fun showResult(roomId: Long, winner: RoundNumber, myRounds: List<Long>) {
        _state.update { it.copy(result = RoundResult(winner.roundId, winner.number, winner.color), message = null) }
        viewModelScope.launch {
            try {
                updateWallet()
                if (myRounds.isEmpty()) return@launch
                // El premio sale del historial: lo calcula el backend, no la aplicación.
                val bets = history.page(1, RESULT_PAGE_SIZE).results.filter {
                    it.source == "roulette" && it.roomId == roomId && it.roundId in myRounds && it.payout != null
                }
                _state.update { current ->
                    val result = current.result
                    if (result?.roundId != winner.roundId || current.room?.id != roomId) current
                    else current.copy(
                        result = result.copy(
                            staked = bets.sumOf { it.amount.toBigDecimal() },
                            payout = bets.sumOf { it.payout?.toBigDecimalOrNull() ?: BigDecimal.ZERO },
                        ),
                    )
                }
            } catch (_: ApiException) {
                showMessage("No se pudo actualizar tu resultado. Revísalo en Apuestas.", isError = true)
            }
        }
    }

    private fun refreshWallet() {
        viewModelScope.launch {
            try {
                updateWallet()
            } catch (_: ApiException) {
                // El saldo se vuelve a pedir al apostar y al terminar cada ronda.
            }
        }
    }

    private suspend fun updateWallet() {
        val wallet = walletRepository.summary()
        _state.update { it.copy(wallet = wallet) }
    }

    fun selectKind(kind: BetKind) = _state.update { it.copy(kind = kind, selection = null) }

    fun select(selection: String) = _state.update { it.copy(selection = selection) }

    fun addChip(value: Int) = _state.update {
        if (it.chips.size >= MAX_CHIPS) it else it.copy(chips = it.chips + value)
    }

    fun clearChips() = _state.update { it.copy(chips = emptyList()) }

    fun placeBet() {
        val current = _state.value
        val room = current.room ?: return
        val round = current.table?.round ?: return
        val selection = current.selection ?: return
        if (current.placing || current.blockedReason != null) return

        val draft = BetRequest(round.id, current.kind, selection, current.chips, UUID.randomUUID().toString())
        val request = unconfirmed?.takeIf { it.copy(reference = draft.reference) == draft } ?: draft
        unconfirmed = request
        _state.update { it.copy(placing = true, message = null) }
        viewModelScope.launch {
            try {
                val bet = roulette.placeBet(room.id, request)
                unconfirmed = null
                if (_state.value.room?.id == room.id) {
                    pendingRounds += bet.roundId
                    _state.update {
                        it.copy(
                            placing = false, chips = emptyList(), roundBets = it.roundBets + bet,
                            message = "Apuesta aceptada.", messageIsError = false,
                        )
                    }
                } else {
                    _state.update { it.copy(placing = false) }
                }
                refreshWallet()
            } catch (error: ApiException) {
                val rejected = error.status != null && error.status < 500
                if (rejected) unconfirmed = null
                // Sin respuesta pudo aceptarse igual: al liquidar se confirma con el historial.
                else if (_state.value.room?.id == room.id) pendingRounds += round.id
                _state.update { it.copy(placing = false) }
                if (error.code == "daily_limit_required") {
                    _state.update { it.copy(showLimitDialog = true, limitError = null) }
                } else {
                    showMessage(error.message, isError = true)
                }
            }
        }
    }

    private fun showMessage(text: String, isError: Boolean) =
        _state.update { it.copy(message = text, messageIsError = isError) }

    fun closeLimitDialog() = _state.update { it.copy(showLimitDialog = false) }

    fun saveLimit(amount: String) {
        if (_state.value.savingLimit) return
        _state.update { it.copy(savingLimit = true, limitError = null) }
        viewModelScope.launch {
            try {
                dailyLimit.set(amount)
                _state.update { it.copy(savingLimit = false, showLimitDialog = false) }
                showMessage("Límite diario guardado. Ya puedes apostar.", isError = false)
            } catch (error: ApiException) {
                _state.update { it.copy(savingLimit = false, limitError = error.message) }
            }
        }
    }

    private companion object {
        const val POLL_MILLIS = 1_000L
        const val MAX_CHIPS = 100
        const val RESULT_PAGE_SIZE = 100
    }
}
