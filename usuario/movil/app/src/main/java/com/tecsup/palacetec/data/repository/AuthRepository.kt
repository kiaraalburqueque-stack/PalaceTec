package com.tecsup.palacetec.data.repository

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
) {
    val session: StateFlow<Session> = sessionStore.session

    suspend fun login(email: String, password: String) {
        val response = apiCall { api.login(LoginRequest(email, password)) }
        sessionStore.save(response.accessToken, response.email)
    }

    suspend fun register(email: String, password: String, dni: String, birthDate: String) {
        apiCall { api.register(RegisterRequest(email, password, dni, birthDate)) }
    }

    /** El backend no tiene logout: basta con olvidar el token. */
    suspend fun logout() = sessionStore.clear()
}
