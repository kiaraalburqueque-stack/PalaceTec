package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.remote.BalanceHistory
import com.tecsup.palacetec.data.remote.BiggestWin
import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.TopGames
import com.tecsup.palacetec.data.remote.WinRate
import com.tecsup.palacetec.data.remote.apiCall

class StatsRepository(private val api: PalaceTecApi) {
    suspend fun winRate(): WinRate = apiCall { api.winRate() }

    suspend fun biggestWin(): BiggestWin? = apiCall { api.biggestWin() }.record

    suspend fun balanceHistory(days: Int): BalanceHistory = apiCall { api.balanceHistory(days) }

    suspend fun topGames(): TopGames = apiCall { api.topGames() }
}
