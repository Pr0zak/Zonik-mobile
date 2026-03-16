package com.zonik.app.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncState(
    val isSyncing: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val artistCount: Int = 0,
    val albumCount: Int = 0,
    val lastSyncResult: String? = null
)

@Singleton
class SyncManager @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) {
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun fullSync() {
        if (_syncState.value.isSyncing) return

        _syncState.value = SyncState(isSyncing = true, status = "Syncing artists...")

        try {
            libraryRepository.syncArtists()
            _syncState.value = _syncState.value.copy(status = "Syncing albums...")

            libraryRepository.syncAlbums()
            _syncState.value = _syncState.value.copy(status = "Sync complete")

            settingsRepository.updateLastSyncTime(System.currentTimeMillis())

            _syncState.value = SyncState(
                isSyncing = false,
                lastSyncResult = "Sync complete"
            )
        } catch (e: Exception) {
            _syncState.value = SyncState(
                isSyncing = false,
                error = e.message ?: "Sync failed",
                lastSyncResult = "Sync failed: ${e.message}"
            )
        }
    }

    fun clearError() {
        _syncState.value = _syncState.value.copy(error = null)
    }
}
