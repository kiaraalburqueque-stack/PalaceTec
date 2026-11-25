package com.tecsup.palacetec.data.remote

import com.google.gson.annotations.SerializedName

/** Cuenta apuestas resueltas, no rondas. Los porcentajes van de 0 a 100. */
data class WinRate(
    val won: Long,
    val lost: Long,
    val push: Long,
    val total: Long,
    @SerializedName("win_rate") val winRate: Double,
    @SerializedName("lost_rate") val lostRate: Double,
    val scope: String,
    val timezone: String,
)

/** record es null si el jugador todavía no ganó. */
data class BiggestWinResponse(val record: BiggestWin?)

data class BiggestWin(
    val net: String,
    val payout: String,
    val amount: String,
    val source: String,
    val game: String,
    @SerializedName("round_id") val roundId: Long?,
    @SerializedName("bet_id") val betId: Long,
    @SerializedName("created_at") val createdAt: String,
)

data class BalanceHistory(
    val period: Int,
    @SerializedName("opening_balance") val openingBalance: String,
    @SerializedName("closing_balance") val closingBalance: String,
    val timezone: String,
    /** Un punto por día de Lima, del más antiguo al de hoy. */
    val points: List<BalancePoint>,
)

data class BalancePoint(val date: String, val balance: String, val movements: Int)

data class TopGames(
    val results: List<TopGame>,
    @SerializedName("total_rounds") val totalRounds: Long,
    val timezone: String,
)

data class TopGame(
    @SerializedName("game_id") val gameId: Long,
    val title: String,
    val category: String,
    val rounds: Long,
    val wagered: String,
)
