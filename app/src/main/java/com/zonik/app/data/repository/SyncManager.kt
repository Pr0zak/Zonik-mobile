package com.zonik.app.data.repository

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

        try {
            // Artists
            val artistCount = libraryRepository.syncArtists()
            _syncState.value = _syncState.value.copy(
                phase = "Syncing albums...",
                detail = "$artistCount artists synced",
                artistCount = artistCount
            )

            // Albums
            var albumCount = 0
            libraryRepository.syncAlbums { fetched ->
                albumCount = fetched
                _syncState.value = _syncState.value.copy(
                    detail = "$artistCount artists \u00b7 $fetched albums fetched..."
                )
            }
            _syncState.value = _syncState.value.copy(
                phase = "Syncing tracks...",
                detail = "$artistCount artists \u00b7 $albumCount albums",
                albumCount = albumCount
            )

            // Tracks (per album)
            var trackCount = 0
            libraryRepository.syncAllTracks { albumsDone, totalAlbums, tracks ->
                trackCount = tracks
                _syncState.value = _syncState.value.copy(
                    detail = "Albums $albumsDone/$totalAlbums \u00b7 $tracks tracks"
                )
            }
            _syncState.value = _syncState.value.copy(
                phase = "Syncing playlists...",
                detail = "$artistCount artists \u00b7 $albumCount albums \u00b7 $trackCount tracks",
                trackCount = trackCount
            )

            // Playlists
            val playlistCount = libraryRepository.syncPlaylists()
            _syncState.value = _syncState.value.copy(
                playlistCount = playlistCount
            )

            settingsRepository.updateLastSyncTime(System.currentTimeMillis())

            val summary = "$artistCount artists \u00b7 $albumCount albums \u00b7 $trackCount tracks \u00b7 $playlistCount playlists"
            _syncState.value = SyncState(
                isSyncing = false,
                artistCount = artistCount,
                albumCount = albumCount,
                trackCount = trackCount,
                playlistCount = playlistCount,
                lastSyncResult = "Sync complete: $summary"
            )
        } catch (e: Exception) {
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
