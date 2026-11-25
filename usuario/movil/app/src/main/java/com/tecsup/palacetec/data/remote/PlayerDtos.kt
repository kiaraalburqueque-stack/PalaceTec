package com.tecsup.palacetec.data.remote

import com.google.gson.annotations.SerializedName

/** El dinero viaja como texto con dos decimales: "100.00". */
data class WalletSummary(
    val balance: String,
    @SerializedName("bonus_balance") val bonusBalance: String,
    @SerializedName("winnings_balance") val winningsBalance: String,
    @SerializedName("low_balance_threshold") val lowBalanceThreshold: String,
    @SerializedName("is_low_balance") val isLowBalance: Boolean,
)

data class Game(
    val id: Long,
    val slug: String,
    val title: String,
    val category: String,
    @SerializedName("category_slug") val categorySlug: String,
    val rtp: String?,
    @SerializedName("cover_url") val coverUrl: String?,
    @SerializedName("min_bet") val minBet: String?,
    @SerializedName("max_bet") val maxBet: String?,
    /** Solo los juegos con salas de ruleta la traen. */
    @SerializedName("rooms_url") val roomsUrl: String?,
    @SerializedName("play_path") val playPath: String?,
    @SerializedName("is_favorite") val isFavorite: Boolean,
)

data class DailyLimitRequest(val amount: String)

data class DailyLimit(
    val configured: Boolean,
    val limit: String?,
    val spent: String,
    val remaining: String?,
    @SerializedName("pending_limit") val pendingLimit: String?,
    @SerializedName("effective_at") val effectiveAt: String?,
    val date: String,
    val timezone: String,
)
