package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.local.HistoryDao
import com.tecsup.palacetec.data.local.Session
import com.tecsup.palacetec.data.local.SessionStore
import com.tecsup.palacetec.data.local.toEntity
import com.tecsup.palacetec.data.local.toItem
import com.tecsup.palacetec.data.remote.HistoryItem
import com.tecsup.palacetec.data.remote.HistoryPage
import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.apiCall

class HistoryRepository(
    private val api: PalaceTecApi,
    private val dao: HistoryDao,
    private val sessionStore: SessionStore,
) {
    suspend fun page(page: Int, pageSize: Int): HistoryPage = apiCall { api.betsHistory(page, pageSize) }

    /** Pide la primera página y la deja guardada para verla sin conexión. */
    suspend fun refresh(pageSize: Int): HistoryPage {
        val owner = owner()
        val page = page(1, pageSize)
        if (owner != null && owner == owner()) {
            dao.replace(owner, page.results.mapIndexed { index, item -> item.toEntity(owner, index) })
        }
        return page
    }

    suspend fun cached(): List<HistoryItem> = owner()?.let { owner -> dao.find(owner).map { it.toItem() } }.orEmpty()

    private fun owner(): String? = (sessionStore.session.value as? Session.LoggedIn)?.email
}
