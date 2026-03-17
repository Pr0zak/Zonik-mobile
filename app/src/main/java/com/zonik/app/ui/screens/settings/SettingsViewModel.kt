package com.zonik.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.api.AppUpdate
import com.zonik.app.data.api.LogUploader
import com.zonik.app.data.api.UpdateChecker
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.data.repository.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val crossfadeEnabled: Boolean = false,
    val crossfadeDuration: Int = 3,
    val lastFmConnected: Boolean = false,
    val scrobblingEnabled: Boolean = false,
    val pendingScrobbleCount: Int = 0,
    val cacheSizeMb: String = "0 MB",
    val libraryStats: LibraryStats = LibraryStats(),
    val serverVersion: String = "",
    val serverType: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val updateChecker: UpdateChecker,
    private val syncManager: SyncManager,
    private val logUploader: LogUploader
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

    private val _crossfadeEnabled = MutableStateFlow(false)
    private val _crossfadeDuration = MutableStateFlow(3)

    init {
        checkForUpdate()
        fetchServerInfo()
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
        _crossfadeEnabled,
        _crossfadeDuration,
        settingsRepository.lastFmSessionKey,
        settingsRepository.scrobblingEnabled,
        libraryStats,
        _serverVersion,
        _serverType
    ) { values ->
        val serverConfig = values[0] as com.zonik.app.model.ServerConfig?
        val isLoggedIn = values[1] as Boolean
        val syncInterval = values[2] as Int
        val wifiOnly = values[3] as Boolean
        val lastSync = values[4] as Long
        val wifiBr = values[5] as Int
        val cellBr = values[6] as Int
        val crossfade = values[7] as Boolean
        val crossfadeDur = values[8] as Int
        val lastFmKey = values[9] as String?
        val scrobbling = values[10] as Boolean
        val stats = values[11] as LibraryStats
        val serverVer = values[12] as String
        val serverTp = values[13] as String?

        SettingsUiState(
            serverUrl = serverConfig?.url ?: "",
            username = serverConfig?.username ?: "",
            isLoggedIn = isLoggedIn,
            syncIntervalMinutes = syncInterval,
            wifiOnly = wifiOnly,
            lastSyncTime = lastSync,
            wifiBitrate = wifiBr,
            cellularBitrate = cellBr,
            crossfadeEnabled = crossfade,
            crossfadeDuration = crossfadeDur,
            lastFmConnected = lastFmKey != null,
            scrobblingEnabled = scrobbling,
            libraryStats = stats,
            serverVersion = serverVer,
            serverType = serverTp
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

    fun fullResync() {
        viewModelScope.launch { syncManager.fullSync() }
    }

    fun setWifiBitrate(bitrate: Int) {
        viewModelScope.launch { settingsRepository.setWifiBitrate(bitrate) }
    }

    fun setCellularBitrate(bitrate: Int) {
        viewModelScope.launch { settingsRepository.setCellularBitrate(bitrate) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
    }

    fun setCrossfadeDuration(seconds: Int) {
        _crossfadeDuration.value = seconds
    }

    fun toggleLastFm() {
        viewModelScope.launch {
            if (uiState.value.lastFmConnected) {
                settingsRepository.setLastFmSessionKey(null)
                settingsRepository.setScrobblingEnabled(false)
            } else {
                settingsRepository.setLastFmSessionKey("stub_session_key")
            }
        }
    }

    fun setScrobblingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setScrobblingEnabled(enabled) }
    }

    fun clearCache() {
        // Stub: clear media cache
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
}
