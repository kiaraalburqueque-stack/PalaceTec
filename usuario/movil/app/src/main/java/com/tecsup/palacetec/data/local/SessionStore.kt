package com.tecsup.palacetec.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.sessionDataStore by preferencesDataStore(name = "session")

sealed interface Session {
    data object Loading : Session
    data object LoggedOut : Session
    data class LoggedIn(val token: String, val email: String) : Session
}

/** Guarda el JWT en DataStore y lo publica como estado observable. */
class SessionStore(context: Context, scope: CoroutineScope) {

    private val dataStore = context.applicationContext.sessionDataStore

    val session: StateFlow<Session> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences ->
            val token = preferences[TOKEN]
            if (token.isNullOrEmpty()) Session.LoggedOut
            else Session.LoggedIn(token, preferences[EMAIL].orEmpty())
        }
        .stateIn(scope, SharingStarted.Eagerly, Session.Loading)

    val token: String? get() = (session.value as? Session.LoggedIn)?.token

    suspend fun save(token: String, email: String) {
        dataStore.edit {
            it[TOKEN] = token
            it[EMAIL] = email
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Solo cierra si el token rechazado sigue siendo el actual. */
    suspend fun clearIfCurrent(rejectedToken: String) {
        dataStore.edit { if (it[TOKEN] == rejectedToken) it.clear() }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("access_token")
        val EMAIL = stringPreferencesKey("email")
    }
}
