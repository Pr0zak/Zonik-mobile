package com.zonik.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.api.AppUpdate
import com.zonik.app.data.api.UpdateChecker
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.data.repository.SyncManager
import com.zonik.app.model.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// region ViewModel

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
    val cacheSizeMb: String = "0 MB"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updateChecker: UpdateChecker,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AppUpdate?>(null)
    val availableUpdate: StateFlow<AppUpdate?> = _availableUpdate.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    init {
        checkForUpdate()
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

    private val _wifiOnly = MutableStateFlow(true)
    private val _crossfadeEnabled = MutableStateFlow(false)
    private val _crossfadeDuration = MutableStateFlow(3)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.serverConfig,
        settingsRepository.isLoggedIn,
        settingsRepository.syncIntervalMinutes,
        _wifiOnly,
        settingsRepository.lastSyncTime,
        settingsRepository.wifiBitrate,
        settingsRepository.cellularBitrate,
        _crossfadeEnabled,
        _crossfadeDuration,
        settingsRepository.lastFmSessionKey,
        settingsRepository.scrobblingEnabled
    ) { values ->
        val serverConfig = values[0] as ServerConfig?
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
            scrobblingEnabled = scrobbling
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSyncInterval(minutes) }
    }

    fun setWifiOnly(enabled: Boolean) {
        _wifiOnly.value = enabled
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
                // Stub: in reality this would launch an OAuth flow
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

    fun disconnect() {
        viewModelScope.launch { settingsRepository.clearAll() }
    }
}

// endregion

// region Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDisconnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate away when disconnected
    LaunchedEffect(uiState.isLoggedIn) {
        if (!uiState.isLoggedIn && uiState.serverUrl.isEmpty()) {
            // Only navigate if we were previously logged in and state cleared
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Server section
            SettingsSectionHeader(title = "Server")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Server URL") },
                        supportingContent = {
                            Text(
                                text = uiState.serverUrl.ifEmpty { "Not connected" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Dns, contentDescription = null)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Username") },
                        supportingContent = {
                            Text(
                                text = uiState.username.ifEmpty { "N/A" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            TextButton(
                                onClick = {
                                    viewModel.disconnect()
                                    onDisconnected()
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Disconnect")
                            }
                        }
                    )
                }
            }

            // Sync section
            SettingsSectionHeader(title = "Sync")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    SyncIntervalDropdown(
                        currentMinutes = uiState.syncIntervalMinutes,
                        onIntervalSelected = viewModel::setSyncInterval
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Wi-Fi only") },
                        supportingContent = { Text("Only sync over Wi-Fi") },
                        leadingContent = {
                            Icon(Icons.Default.Wifi, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.wifiOnly,
                                onCheckedChange = viewModel::setWifiOnly
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Last synced") },
                        supportingContent = {
                            Text(
                                text = if (uiState.lastSyncTime > 0L) {
                                    formatTimestamp(uiState.lastSyncTime)
                                } else {
                                    "Never"
                                }
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = viewModel::syncNow) {
                                    Text("Sync Now")
                                }
                                OutlinedButton(onClick = viewModel::fullResync) {
                                    Text("Full Resync")
                                }
                            }
                        }
                    )
                }
            }

            // Playback section
            SettingsSectionHeader(title = "Playback")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    BitrateDropdown(
                        label = "Wi-Fi max bitrate",
                        currentBitrate = uiState.wifiBitrate,
                        onBitrateSelected = viewModel::setWifiBitrate
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BitrateDropdown(
                        label = "Cellular max bitrate",
                        currentBitrate = uiState.cellularBitrate,
                        onBitrateSelected = viewModel::setCellularBitrate
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Crossfade") },
                        leadingContent = {
                            Icon(Icons.Default.Tune, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.crossfadeEnabled,
                                onCheckedChange = viewModel::setCrossfadeEnabled
                            )
                        }
                    )
                    AnimatedVisibility(visible = uiState.crossfadeEnabled) {
                        ListItem(
                            headlineContent = {
                                Text("Duration: ${uiState.crossfadeDuration}s")
                            },
                            supportingContent = {
                                Slider(
                                    value = uiState.crossfadeDuration.toFloat(),
                                    onValueChange = { viewModel.setCrossfadeDuration(it.toInt()) },
                                    valueRange = 1f..10f,
                                    steps = 8
                                )
                            }
                        )
                    }
                }
            }

            // Last.fm section
            SettingsSectionHeader(title = "Last.fm")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = {
                            if (uiState.lastFmConnected) {
                                OutlinedButton(onClick = viewModel::toggleLastFm) {
                                    Text("Disconnect Last.fm")
                                }
                            } else {
                                OutlinedButton(onClick = viewModel::toggleLastFm) {
                                    Text("Connect Last.fm")
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (uiState.lastFmConnected) Icons.Default.Link else Icons.Default.LinkOff,
                                contentDescription = null
                            )
                        }
                    )
                    if (uiState.lastFmConnected) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Scrobbling") },
                            leadingContent = {
                                Icon(Icons.Default.MusicNote, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = uiState.scrobblingEnabled,
                                    onCheckedChange = viewModel::setScrobblingEnabled
                                )
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ListItem(
                            headlineContent = { Text("Pending scrobbles") },
                            supportingContent = {
                                Text("${uiState.pendingScrobbleCount}")
                            },
                            leadingContent = {
                                Icon(Icons.Default.Pending, contentDescription = null)
                            }
                        )
                    }
                }
            }

            // Cache section
            SettingsSectionHeader(title = "Cache")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Cache size") },
                        supportingContent = { Text(uiState.cacheSizeMb) },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            OutlinedButton(onClick = viewModel::clearCache) {
                                Text("Clear Cache")
                            }
                        }
                    )
                }
            }

            // Updates section
            SettingsSectionHeader(title = "Updates")
            UpdateSection(viewModel = viewModel)

            // About section
            SettingsSectionHeader(title = "About")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                ListItem(
                    headlineContent = { Text("App version") },
                    supportingContent = { Text("Zonik v0.1.0") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// endregion

// region Components

@Composable
private fun UpdateSection(viewModel: SettingsViewModel) {
    val availableUpdate by viewModel.availableUpdate.collectAsState()
    val isChecking by viewModel.isCheckingUpdate.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            val update = availableUpdate
            if (update != null) {
                ListItem(
                    headlineContent = { Text("Update available: v${update.version}") },
                    supportingContent = {
                        if (update.releaseNotes.isNotBlank()) {
                            Text(update.releaseNotes, maxLines = 3)
                        }
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                val progress = updateProgress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                } else {
                    ListItem(
                        headlineContent = {
                            Button(onClick = viewModel::downloadUpdate) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download & Install")
                            }
                        }
                    )
                }
            } else {
                ListItem(
                    headlineContent = {
                        if (isChecking) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Checking for updates...")
                            }
                        } else {
                            Text("You're up to date")
                        }
                    },
                    leadingContent = {
                        if (!isChecking) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                        }
                    },
                    trailingContent = {
                        if (!isChecking) {
                            TextButton(onClick = viewModel::checkForUpdate) {
                                Text("Check")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SyncIntervalDropdown(
    currentMinutes: Int,
    onIntervalSelected: (Int) -> Unit
) {
    val options = listOf(
        0 to "Off",
        15 to "15 minutes",
        60 to "1 hour",
        360 to "6 hours",
        1440 to "Daily"
    )
    val currentLabel = options.find { it.first == currentMinutes }?.second ?: "1 hour"
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Sync interval") },
        supportingContent = { Text(currentLabel) },
        leadingContent = {
            Icon(Icons.Default.Sync, contentDescription = null)
        },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(currentLabel)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onIntervalSelected(minutes)
                                expanded = false
                            },
                            trailingIcon = if (minutes == currentMinutes) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun BitrateDropdown(
    label: String,
    currentBitrate: Int,
    onBitrateSelected: (Int) -> Unit
) {
    val options = listOf(
        0 to "Original",
        320 to "320 kbps",
        256 to "256 kbps",
        192 to "192 kbps",
        128 to "128 kbps"
    )
    val currentLabel = options.find { it.first == currentBitrate }?.second ?: "Original"
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(currentLabel) },
        leadingContent = {
            Icon(Icons.Default.Speed, contentDescription = null)
        },
        trailingContent = {
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(currentLabel)
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (bitrate, optionLabel) ->
                        DropdownMenuItem(
                            text = { Text(optionLabel) },
                            onClick = {
                                onBitrateSelected(bitrate)
                                expanded = false
                            },
                            trailingIcon = if (bitrate == currentBitrate) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }
    )
}

private fun formatTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}

// endregion
