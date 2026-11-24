package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.remote.DailyLimit
import com.tecsup.palacetec.data.remote.DailyLimitRequest
import com.tecsup.palacetec.data.remote.Game
import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.WalletSummary
import com.tecsup.palacetec.data.remote.apiCall

class WalletRepository(private val api: PalaceTecApi) {
    suspend fun summary(): WalletSummary = apiCall { api.walletSummary() }
}

class CatalogRepository(private val api: PalaceTecApi) {
    suspend fun games(): List<Game> = apiCall { api.games() }
}

class DailyLimitRepository(private val api: PalaceTecApi) {
    suspend fun get(): DailyLimit = apiCall { api.dailyLimit() }

    suspend fun set(amount: String): DailyLimit = apiCall { api.setDailyLimit(DailyLimitRequest(amount)) }
}
