package com.tecsup.palacetec.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val email: String? get() = (session.value as? Session.LoggedIn)?.email

    private val _notice = MutableStateFlow<String?>(null)

    /** Motivo por el que el backend cerró la sesión; se muestra en el login. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    suspend fun save(token: String, email: String) {
        _notice.value = null
        dataStore.edit {
            it[TOKEN] = token
            it[EMAIL] = email
        }
    }

    suspend fun clear() {
        _notice.value = null
        dataStore.edit { it.clear() }
    }

    /** Solo cierra si el token rechazado sigue siendo el actual. */
    suspend fun clearIfCurrent(rejectedToken: String, message: String?) {
        dataStore.edit {
            if (it[TOKEN] == rejectedToken) {
                it.clear()
                _notice.value = message ?: "Tu sesión terminó. Inicia sesión nuevamente."
            }
        }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("access_token")
        val EMAIL = stringPreferencesKey("email")
    }
}
