package com.zonik.wear.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zonik.core.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wear_settings")

/**
 * Wear-side persistence for the ServerConfig (url + username + apiKey).
 * Kept independent from :app's SettingsRepository — different process,
 * different DataStore file, simpler key set.
 */
class WearSettingsRepository(context: Context) {

    private val store = context.dataStore

    val serverConfig: Flow<ServerConfig?> = store.data.map { prefs ->
        val url = prefs[SERVER_URL] ?: return@map null
        val username = prefs[USERNAME] ?: return@map null
        val apiKey = prefs[API_KEY] ?: return@map null
        ServerConfig(url, username, apiKey)
    }

    suspend fun current(): ServerConfig? = serverConfig.first()

    suspend fun save(config: ServerConfig) {
        store.edit { prefs ->
            prefs[SERVER_URL] = config.url
            prefs[USERNAME] = config.username
            prefs[API_KEY] = config.apiKey
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val API_KEY = stringPreferencesKey("api_key")
    }
}
