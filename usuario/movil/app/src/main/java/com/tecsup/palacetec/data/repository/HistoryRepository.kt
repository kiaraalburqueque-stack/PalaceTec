package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.local.HistoryDao
import com.tecsup.palacetec.data.local.toEntity
import com.tecsup.palacetec.data.local.toItem
import com.tecsup.palacetec.data.remote.HistoryItem
import com.tecsup.palacetec.data.remote.HistoryPage
import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.apiCall

class HistoryRepository(
    private val api: PalaceTecApi,
    private val dao: HistoryDao,
    private val owner: () -> String?,
) {
    suspend fun page(page: Int, pageSize: Int): HistoryPage = apiCall { api.betsHistory(page, pageSize) }

    /** Pide la primera página y la deja guardada para verla sin conexión. */
    suspend fun refresh(pageSize: Int): HistoryPage {
        val player = owner()
        val page = page(1, pageSize)
        if (player != null && player == owner()) {
            dao.replace(player, page.results.mapIndexed { index, item -> item.toEntity(player, index) })
        }
        return page
    }

    suspend fun cached(): List<HistoryItem> = owner()?.let { player -> dao.find(player).map { it.toItem() } }.orEmpty()
}
