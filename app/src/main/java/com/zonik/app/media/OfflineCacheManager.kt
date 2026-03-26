package com.zonik.app.media

import android.content.Context
import com.zonik.app.data.DebugLog
import com.zonik.app.data.db.ZonikDatabase
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.util.md5
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadState { QUEUED, DOWNLOADING, COMPLETE, FAILED }

@Singleton
class OfflineCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val database: ZonikDatabase,
    private val okHttpClient: OkHttpClient
) {
    private val offlineDir = File(context.filesDir, "offline_tracks").also { it.mkdirs() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private var downloadJob: Job? = null
    private val maxConcurrent = 2

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _offlineTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val offlineTrackIds: StateFlow<Set<String>> = _offlineTrackIds.asStateFlow()

    init {
        // Load existing offline track IDs on startup
        scope.launch {
            refreshOfflineIds()
        }
    }

    fun isOffline(trackId: String): Boolean {
        return File(offlineDir, trackId).exists()
    }

    fun getOfflineFile(trackId: String): File? {
        val file = File(offlineDir, trackId)
        return if (file.exists()) file else null
    }

    fun getStorageUsedBytes(): Long {
        return offlineDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun downloadTracks(trackIds: List<String>) {
        val newIds = trackIds.filter { !isOffline(it) && it !in downloadQueue }
        if (newIds.isEmpty()) return
        downloadQueue.addAll(newIds)
        updateStates(newIds, DownloadState.QUEUED)
        DebugLog.d("OfflineCache", "Queued ${newIds.size} tracks for offline download")
        startProcessing()
    }

    fun downloadTrack(trackId: String) {
        downloadTracks(listOf(trackId))
    }

    fun removeTrack(trackId: String) {
        File(offlineDir, trackId).delete()
        scope.launch {
            database.trackDao().setOfflineCached(trackId, false)
            refreshOfflineIds()
        }
        DebugLog.d("OfflineCache", "Removed offline track: $trackId")
    }

    fun cancelDownloads() {
        downloadJob?.cancel()
        downloadJob = null
        downloadQueue.clear()
        // Clear non-complete states
        _downloadStates.value = _downloadStates.value.filter { it.value == DownloadState.COMPLETE }
        DebugLog.d("OfflineCache", "Downloads cancelled")
    }

    fun clearAll() {
        downloadJob?.cancel()
        downloadQueue.clear()
        _downloadStates.value = emptyMap()
        offlineDir.listFiles()?.forEach { it.delete() }
        scope.launch {
            database.trackDao().clearAllOfflineCached()
            refreshOfflineIds()
        }
        DebugLog.d("OfflineCache", "Cleared all offline tracks")
    }

    private fun startProcessing() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            val semaphore = kotlinx.coroutines.sync.Semaphore(maxConcurrent)
            val jobs = mutableListOf<Job>()

            while (downloadQueue.isNotEmpty()) {
                val trackId = downloadQueue.poll() ?: break

                // Check storage limit (0 = no limit)
                val limitMb = settingsRepository.offlineStorageLimitMb.first()
                val usedMb = getStorageUsedBytes() / (1024 * 1024)
                if (limitMb > 0 && usedMb >= limitMb) {
                    DebugLog.w("OfflineCache", "Storage limit reached (${usedMb}MB / ${limitMb}MB), stopping")
                    updateState(trackId, DownloadState.FAILED)
                    // Drain remaining queue
                    while (downloadQueue.isNotEmpty()) {
                        val remaining = downloadQueue.poll() ?: break
                        updateState(remaining, DownloadState.FAILED)
                    }
                    break
                }

                jobs += launch {
                    semaphore.acquire()
                    try {
                        downloadSingleTrack(trackId)
                    } finally {
                        semaphore.release()
                    }
                }
            }

            jobs.joinAll()
        }
    }

    private suspend fun downloadSingleTrack(trackId: String) {
        if (isOffline(trackId)) {
            updateState(trackId, DownloadState.COMPLETE)
            return
        }
        updateState(trackId, DownloadState.DOWNLOADING)
        try {
            val url = buildStreamUrl(trackId)
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                DebugLog.w("OfflineCache", "Download failed for $trackId: HTTP ${response.code}")
                updateState(trackId, DownloadState.FAILED)
                return
            }
            val body = response.body ?: run {
                updateState(trackId, DownloadState.FAILED)
                return
            }
            // Write to temp file then rename (atomic)
            val tempFile = File(offlineDir, "$trackId.tmp")
            try {
                tempFile.outputStream().use { out ->
                    body.byteStream().use { input ->
                        input.copyTo(out, bufferSize = 8192)
                    }
                }
                val sizeKb = tempFile.length() / 1024
                tempFile.renameTo(File(offlineDir, trackId))
                database.trackDao().setOfflineCached(trackId, true)
                refreshOfflineIds()
                updateState(trackId, DownloadState.COMPLETE)
                DebugLog.d("OfflineCache", "Downloaded: $trackId (${sizeKb}KB)")
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DebugLog.w("OfflineCache", "Download failed for $trackId: ${e.message}")
            updateState(trackId, DownloadState.FAILED)
        }
    }

    private suspend fun buildStreamUrl(trackId: String): String {
        val config = settingsRepository.serverConfig.first()
            ?: throw IllegalStateException("No server config")
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("${config.apiKey}$salt")
        // No maxBitRate — download original quality for offline
        return "${config.url.trimEnd('/')}/rest/stream.view?id=$trackId&estimateContentLength=true&u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp"
    }

    private fun updateState(trackId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value + (trackId to state)
    }

    private fun updateStates(trackIds: List<String>, state: DownloadState) {
        _downloadStates.value = _downloadStates.value + trackIds.associateWith { state }
    }

    private suspend fun refreshOfflineIds() {
        val ids = database.trackDao().getOfflineCachedIds().toSet()
        _offlineTrackIds.value = ids
    }
}
