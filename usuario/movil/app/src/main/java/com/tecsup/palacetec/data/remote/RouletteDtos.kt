package com.tecsup.palacetec.data.remote

import com.google.gson.annotations.SerializedName

data class RouletteRoom(
    val id: Long,
    val name: String,
    @SerializedName("min_bet") val minBet: String,
    @SerializedName("max_bet") val maxBet: String,
    val players: Long,
)

data class RoomState(
    @SerializedName("room_id") val roomId: Long,
    @SerializedName("server_time") val serverTime: String,
    @SerializedName("min_bet") val minBet: String,
    @SerializedName("max_bet") val maxBet: String,
    val round: Round?,
    @SerializedName("chip_values") val chipValues: List<Int>,
    @SerializedName("recent_results") val recentResults: List<RoundNumber>,
    val numbers: List<NumberColor>,
)

data class Round(
    val id: Long,
    /** OPEN, CLOSED o SETTLED. */
    val status: String,
    @SerializedName("opened_at") val openedAt: String,
    @SerializedName("closes_at") val closesAt: String,
    @SerializedName("remaining_seconds") val remainingSeconds: Long,
    @SerializedName("winning_number") val winningNumber: Int?,
) {
    val acceptsBets: Boolean get() = status == "OPEN" && remainingSeconds > 0
}

/** color: rojo, negro o verde. */
data class RoundNumber(
    @SerializedName("round_id") val roundId: Long,
    val number: Int,
    val color: String,
)

data class NumberColor(val number: Int, val color: String)

/** El móvil usa tres de los cinco tipos del backend. */
enum class BetKind(val label: String) {
    STRAIGHT_UP("Pleno"),
    COLOR("Color"),
    PARITY("Paridad"),
}

data class BetRequest(
    @SerializedName("round_id") val roundId: Long,
    val kind: BetKind,
    val selection: String,
    val chips: List<Int>,
    /** UUID: si se reenvía la misma, el backend no cobra dos veces. */
    val reference: String,
)

data class BetResponse(
    val id: Long,
    @SerializedName("round_id") val roundId: Long,
    val kind: String,
    val selection: String,
    val chips: List<Int>,
    val amount: String,
    val payout: String?,
    val reference: String,
    @SerializedName("created_at") val createdAt: String,
)

/** Apuesta de ruleta (source=roulette) o jugada de casino (source=casino, sin ronda ni sala). */
data class HistoryItem(
    val id: Long,
    @SerializedName("round_id") val roundId: Long?,
    val kind: String,
    val selection: String,
    val chips: List<Int>,
    val amount: String,
    val payout: String?,
    val reference: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("room_id") val roomId: Long?,
    val game: String,
    /** PENDING, WON, LOST o PUSH. */
    val status: String,
    val net: String?,
    val source: String,
)

data class HistoryPage(
    val count: Long,
    val next: String?,
    val previous: String?,
    val results: List<HistoryItem>,
)
