package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.analytics.AnalyticsTracker
import com.tecsup.palacetec.data.local.HistoryDao
import com.tecsup.palacetec.data.local.Session
import com.tecsup.palacetec.data.local.SessionStore
import com.tecsup.palacetec.data.remote.LoginRequest
import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.RegisterRequest
import com.tecsup.palacetec.data.remote.apiCall
import kotlinx.coroutines.flow.StateFlow

class AuthRepository(
    private val api: PalaceTecApi,
    private val sessionStore: SessionStore,
    private val historyDao: HistoryDao,
    private val analytics: AnalyticsTracker,
) {
    val session: StateFlow<Session> = sessionStore.session
    val notice: StateFlow<String?> = sessionStore.notice

    suspend fun login(email: String, password: String) {
        val response = apiCall { api.login(LoginRequest(email, password)) }
        // Antes de guardar el token: al guardarlo, la navegación cierra la pantalla de acceso.
        analytics.login()
        sessionStore.save(response.accessToken, response.email)
    }

    suspend fun register(email: String, password: String, dni: String, birthDate: String) {
        apiCall { api.register(RegisterRequest(email, password, dni, birthDate)) }
        analytics.signUp()
    }

    /** El backend no tiene logout: basta con olvidar el token y lo guardado en el celular. */
    suspend fun logout() {
        sessionStore.clear()
        historyDao.deleteAll()
    }
}
