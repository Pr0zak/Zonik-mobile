package com.zonik.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.api.AppUpdate
import com.zonik.app.data.api.LogUploader
import com.zonik.app.data.api.UpdateChecker
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.data.repository.SyncManager
import com.zonik.app.media.PlaybackManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryStats(
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val artistCount: Int = 0,
    val genreCount: Int = 0,
    val totalDurationSeconds: Long = 0L,
    val totalSizeBytes: Long = 0L
)

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val isLoggedIn: Boolean = false,
    val syncIntervalMinutes: Int = 60,
    val wifiOnly: Boolean = true,
    val lastSyncTime: Long = 0L,
    val wifiBitrate: Int = 0,
    val cellularBitrate: Int = 192,
    val cacheSizeBytes: Long = 0L,
    val maxCacheSizeMb: Int = 500,
    val coverArtCacheSizeMb: Int = 250,
    val cacheReadAhead: Int = 3,
    val keepScreenOn: Boolean = false,
    val adaptiveBitrate: Boolean = true,
    val libraryStats: LibraryStats = LibraryStats(),
    val serverVersion: String = "",
    val serverType: String? = null,
    val eqEnabled: Boolean = false,
    val eqPreset: Int = 0,
    val eqBandLevels: String? = null
)

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val updateChecker: UpdateChecker,
    private val syncManager: SyncManager,
    private val logUploader: LogUploader,
    private val simpleCache: SimpleCache,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    val githubToken = settingsRepository.githubToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _logUploadUrl = MutableStateFlow<String?>(null)
    val logUploadUrl: StateFlow<String?> = _logUploadUrl.asStateFlow()

    private val _isUploadingLogs = MutableStateFlow(false)
    val isUploadingLogs: StateFlow<Boolean> = _isUploadingLogs.asStateFlow()

    fun setGithubToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setGithubToken(token.ifBlank { null })
        }
    }

    fun uploadLogs() {
        viewModelScope.launch {
            val token = githubToken.value ?: return@launch
            _isUploadingLogs.value = true
            _logUploadUrl.value = logUploader.uploadLogs(token)
            _isUploadingLogs.value = false
        }
    }

    private val _availableUpdate = MutableStateFlow<AppUpdate?>(null)
    val availableUpdate: StateFlow<AppUpdate?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    private val _serverVersion = MutableStateFlow("")
    private val _serverType = MutableStateFlow<String?>(null)

    private val _cacheSizeBytes = MutableStateFlow(0L)

    init {
        checkForUpdate()
        fetchServerInfo()
        refreshCacheSize()
    }

    private fun refreshCacheSize() {
        _cacheSizeBytes.value = simpleCache.cacheSpace
    }

    private fun fetchServerInfo() {
        viewModelScope.launch {
            try {
                val (version, serverVersion, type) = libraryRepository.getServerInfo()
                _serverVersion.value = serverVersion ?: version
                _serverType.value = type
            } catch (_: Exception) {
                _serverVersion.value = "Unknown"
            }
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _availableUpdate.value = updateChecker.checkForUpdate()
            _isCheckingUpdate.value = false
        }
    }

    fun downloadUpdate() {
        val update = _availableUpdate.value ?: return
        viewModelScope.launch {
            _updateProgress.value = 0f
            val success = updateChecker.downloadAndInstall(update) { progress ->
                _updateProgress.value = progress
            }
            if (!success) _updateProgress.value = null
        }
    }

    private val libraryStats: Flow<LibraryStats> = combine(
        libraryRepository.trackCount(),
        libraryRepository.albumCount(),
        libraryRepository.artistCount(),
        libraryRepository.genreCount(),
        libraryRepository.totalDuration(),
        libraryRepository.totalSize()
    ) { values ->
        LibraryStats(
            trackCount = values[0] as Int,
            albumCount = values[1] as Int,
            artistCount = values[2] as Int,
            genreCount = values[3] as Int,
            totalDurationSeconds = values[4] as Long,
            totalSizeBytes = values[5] as Long
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.serverConfig,
        settingsRepository.isLoggedIn,
        settingsRepository.syncIntervalMinutes,
        settingsRepository.wifiOnly,
        settingsRepository.lastSyncTime,
        settingsRepository.wifiBitrate,
        settingsRepository.cellularBitrate,
        libraryStats,
        _serverVersion,
        _serverType,
        _cacheSizeBytes,
        settingsRepository.audioCacheSizeMb,
        settingsRepository.coverArtCacheSizeMb,
        settingsRepository.cacheReadAhead,
        settingsRepository.keepScreenOn,
        settingsRepository.adaptiveBitrate,
        settingsRepository.eqEnabled,
        settingsRepository.eqPreset,
        settingsRepository.eqBandLevels
    ) { values ->
        val serverConfig = values[0] as com.zonik.app.model.ServerConfig?
        val isLoggedIn = values[1] as Boolean
        val syncInterval = values[2] as Int
        val wifiOnly = values[3] as Boolean
        val lastSync = values[4] as Long
        val wifiBr = values[5] as Int
        val cellBr = values[6] as Int
        val stats = values[7] as LibraryStats
        val serverVer = values[8] as String
        val serverTp = values[9] as String?
        val cacheBytes = values[10] as Long
        val maxCache = values[11] as Int
        val coverArtCache = values[12] as Int
        val readAhead = values[13] as Int
        val screenOn = values[14] as Boolean
        val adaptive = values[15] as Boolean
        val eqOn = values[16] as Boolean
        val eqPr = values[17] as Int
        val eqBands = values[18] as String?

        SettingsUiState(
            serverUrl = serverConfig?.url ?: "",
            username = serverConfig?.username ?: "",
            isLoggedIn = isLoggedIn,
            syncIntervalMinutes = syncInterval,
            wifiOnly = wifiOnly,
            lastSyncTime = lastSync,
            wifiBitrate = wifiBr,
            cellularBitrate = cellBr,
            libraryStats = stats,
            serverVersion = serverVer,
            serverType = serverTp,
            cacheSizeBytes = cacheBytes,
            maxCacheSizeMb = maxCache,
            coverArtCacheSizeMb = coverArtCache,
            cacheReadAhead = readAhead,
            keepScreenOn = screenOn,
            adaptiveBitrate = adaptive,
            eqEnabled = eqOn,
            eqPreset = eqPr,
            eqBandLevels = eqBands
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSyncInterval(minutes) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWifiOnly(enabled) }
    }

    fun syncNow() {
        viewModelScope.launch { syncManager.fullSync() }
    }

    fun setWifiBitrate(bitrate: Int) {
        viewModelScope.launch { settingsRepository.setWifiBitrate(bitrate) }
    }

    fun setCellularBitrate(bitrate: Int) {
        viewModelScope.launch { settingsRepository.setCellularBitrate(bitrate) }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            simpleCache.keys.toList().forEach { key ->
                simpleCache.removeResource(key)
            }
            refreshCacheSize()
        }
    }

    fun setAudioCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch { settingsRepository.setAudioCacheSizeMb(sizeMb) }
    }

    fun setCoverArtCacheSizeMb(sizeMb: Int) {
        viewModelScope.launch { settingsRepository.setCoverArtCacheSizeMb(sizeMb) }
    }

    fun clearCoverArtCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = context.cacheDir.resolve("coil_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        }
    }

    fun setCacheReadAhead(count: Int) {
        viewModelScope.launch { settingsRepository.setCacheReadAhead(count) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setKeepScreenOn(enabled) }
    }

    fun setAdaptiveBitrate(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAdaptiveBitrate(enabled) }
    }

    val autoTabOrder = settingsRepository.autoTabOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("mix", "recent", "library", "playlists"))

    fun setAutoTabOrder(order: List<String>) {
        viewModelScope.launch { settingsRepository.setAutoTabOrder(order) }
    }

    fun moveAutoTab(from: Int, to: Int) {
        val current = autoTabOrder.value.toMutableList()
        if (from in current.indices && to in current.indices) {
            val item = current.removeAt(from)
            current.add(to, item)
            viewModelScope.launch { settingsRepository.setAutoTabOrder(current) }
        }
    }

    fun disconnect() {
        viewModelScope.launch { settingsRepository.clearAll() }
    }

    fun setEqEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEqEnabled(enabled)
            playbackManager.applyEqualizerSettings(enabled, uiState.value.eqPreset, uiState.value.eqBandLevels)
        }
    }

    fun setEqPreset(preset: Int) {
        viewModelScope.launch {
            settingsRepository.setEqPreset(preset)
            settingsRepository.setEqBandLevels(null) // Clear custom when selecting preset
            playbackManager.applyEqualizerSettings(uiState.value.eqEnabled, preset, null)
        }
    }

    fun setEqBandLevel(band: Int, level: Short) {
        viewModelScope.launch {
            val current = uiState.value.eqBandLevels?.split(",")?.mapNotNull { it.toShortOrNull() }?.toMutableList()
                ?: MutableList(5) { 0.toShort() }
            if (band in current.indices) {
                current[band] = level
            }
            val levelsStr = current.joinToString(",")
            settingsRepository.setEqBandLevels(levelsStr)
            settingsRepository.setEqPreset(-1) // Custom
            playbackManager.applyEqualizerSettings(uiState.value.eqEnabled, -1, levelsStr)
        }
    }
}
