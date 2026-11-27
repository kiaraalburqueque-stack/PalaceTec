package com.tecsup.palacetec.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

val Context.pushDataStore: DataStore<Preferences> by preferencesDataStore(name = "push")

/** Va aparte de la sesión: cerrar sesión no debe volver a pedir el permiso. */
class PushStore(private val dataStore: DataStore<Preferences>) {

    suspend fun permissionAsked(): Boolean = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .first()[ASKED] ?: false

    suspend fun setPermissionAsked() {
        dataStore.edit { it[ASKED] = true }
    }

    private companion object {
        val ASKED = booleanPreferencesKey("permission_asked")
    }
}
