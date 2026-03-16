package com.zonik.app.data.repository

import com.zonik.app.data.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncState(
    val isSyncing: Boolean = false,
    val phase: String = "",
    val detail: String = "",
    val error: String? = null,
    val artistCount: Int = 0,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val playlistCount: Int = 0,
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

        _syncState.value = SyncState(isSyncing = true, phase = "Syncing artists...")
        DebugLog.d("Sync", "Starting full sync (search3 method)")

        try {
            // Artists via search3 empty query
            val artistCount = libraryRepository.syncArtists { fetched ->
                _syncState.value = _syncState.value.copy(
                    detail = "$fetched artists fetched..."
                )
            }
            DebugLog.d("Sync", "Artists synced: $artistCount")

            // Albums via search3 empty query
            _syncState.value = _syncState.value.copy(
                phase = "Syncing albums...",
                artistCount = artistCount
            )
            val albumCount = libraryRepository.syncAlbums { fetched ->
                _syncState.value = _syncState.value.copy(
                    detail = "$artistCount artists \u00b7 $fetched albums fetched..."
                )
            }
            DebugLog.d("Sync", "Albums synced: $albumCount")

            // Tracks via search3 empty query (bulk, not per-album)
            _syncState.value = _syncState.value.copy(
                phase = "Syncing tracks...",
                albumCount = albumCount
            )
            val trackCount = libraryRepository.syncAllTracks { fetched ->
                _syncState.value = _syncState.value.copy(
                    detail = "$artistCount artists \u00b7 $albumCount albums \u00b7 $fetched tracks fetched..."
                )
            }
            DebugLog.d("Sync", "Tracks synced: $trackCount")

            // Playlists
            _syncState.value = _syncState.value.copy(
                phase = "Syncing playlists...",
                trackCount = trackCount
            )
            val playlistCount = libraryRepository.syncPlaylists()
            DebugLog.d("Sync", "Playlists synced: $playlistCount")

            settingsRepository.updateLastSyncTime(System.currentTimeMillis())

            val summary = "$artistCount artists \u00b7 $albumCount albums \u00b7 $trackCount tracks \u00b7 $playlistCount playlists"
            DebugLog.d("Sync", "Sync complete: $summary")
            _syncState.value = SyncState(
                isSyncing = false,
                artistCount = artistCount,
                albumCount = albumCount,
                trackCount = trackCount,
                playlistCount = playlistCount,
                lastSyncResult = "Sync complete: $summary"
            )
        } catch (e: Exception) {
            DebugLog.e("Sync", "Sync failed", e)
            _syncState.value = _syncState.value.copy(
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
