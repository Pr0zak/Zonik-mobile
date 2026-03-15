package com.zonik.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.zonik.app.model.ServerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val serverConfig: Flow<ServerConfig?> = dataStore.data.map { prefs ->
        val url = prefs[SERVER_URL] ?: return@map null
        val username = prefs[USERNAME] ?: return@map null
        val apiKey = prefs[API_KEY] ?: return@map null
        ServerConfig(url, username, apiKey)
    }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SERVER_URL] != null && prefs[USERNAME] != null && prefs[API_KEY] != null
    }

    val wifiBitrate: Flow<Int> = dataStore.data.map { prefs ->
        prefs[WIFI_BITRATE] ?: 0
    }

    val cellularBitrate: Flow<Int> = dataStore.data.map { prefs ->
        prefs[CELLULAR_BITRATE] ?: 192
    }

    val lastSyncTime: Flow<Long> = dataStore.data.map { prefs ->
        prefs[LAST_SYNC] ?: 0L
    }

    val syncIntervalMinutes: Flow<Int> = dataStore.data.map { prefs ->
        prefs[SYNC_INTERVAL] ?: 60
    }

    val scrobblingEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SCROBBLING_ENABLED] ?: false
    }

    val lastFmSessionKey: Flow<String?> = dataStore.data.map { prefs ->
        prefs[LASTFM_SESSION_KEY]
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = config.url
            prefs[USERNAME] = config.username
            prefs[API_KEY] = config.apiKey
        }
    }

    suspend fun updateLastSyncTime(time: Long) {
        dataStore.edit { prefs -> prefs[LAST_SYNC] = time }
    }

    suspend fun setSyncInterval(minutes: Int) {
        dataStore.edit { prefs -> prefs[SYNC_INTERVAL] = minutes }
    }

    suspend fun setWifiBitrate(bitrate: Int) {
        dataStore.edit { prefs -> prefs[WIFI_BITRATE] = bitrate }
    }

    suspend fun setCellularBitrate(bitrate: Int) {
        dataStore.edit { prefs -> prefs[CELLULAR_BITRATE] = bitrate }
    }

    suspend fun setScrobblingEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SCROBBLING_ENABLED] = enabled }
    }

    suspend fun setLastFmSessionKey(key: String?) {
        dataStore.edit { prefs ->
            if (key != null) prefs[LASTFM_SESSION_KEY] = key
            else prefs.remove(LASTFM_SESSION_KEY)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val USERNAME = stringPreferencesKey("username")
        private val API_KEY = stringPreferencesKey("api_key")
        private val WIFI_BITRATE = intPreferencesKey("wifi_bitrate")
        private val CELLULAR_BITRATE = intPreferencesKey("cellular_bitrate")
        private val LAST_SYNC = longPreferencesKey("last_sync")
        private val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        private val SCROBBLING_ENABLED = booleanPreferencesKey("scrobbling_enabled")
        private val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
    }
}
