package com.zonik.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.zonik.app.data.DebugLog
import com.zonik.app.ui.util.formatLargeDuration
import com.zonik.app.ui.util.formatLargeFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDisconnected: () -> Unit,
    onNavigateToStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedIn) {
        if (!uiState.isLoggedIn && uiState.serverUrl.isEmpty()) {
            // Only navigate if we were previously logged in and state cleared
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                windowInsets = WindowInsets(0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Library Stats section (at top)
            SettingsSectionHeader(title = "Library Stats")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val stats = uiState.libraryStats
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "Tracks", value = "%,d".format(stats.trackCount))
                        StatItem(label = "Albums", value = "%,d".format(stats.albumCount))
                        StatItem(label = "Artists", value = "%,d".format(stats.artistCount))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "Genres", value = "%,d".format(stats.genreCount))
                        StatItem(label = "Duration", value = formatLargeDuration(stats.totalDurationSeconds))
                        StatItem(label = "Size", value = formatLargeFileSize(stats.totalSizeBytes))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onNavigateToStats,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Full Stats")
                    }
                }
            }

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
                        headlineContent = { Text("Server version") },
                        supportingContent = {
                            Text(
                                text = buildString {
                                    append(uiState.serverVersion.ifEmpty { "Unknown" })
                                    uiState.serverType?.let { append(" ($it)") }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Cloud, contentDescription = null)
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
                        icon = Icons.Default.Wifi,
                        currentBitrate = uiState.wifiBitrate,
                        onBitrateSelected = viewModel::setWifiBitrate
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    BitrateDropdown(
                        label = "Cellular max bitrate",
                        icon = Icons.Default.SignalCellularAlt,
                        currentBitrate = uiState.cellularBitrate,
                        onBitrateSelected = viewModel::setCellularBitrate
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Keep screen on") },
                        supportingContent = { Text("Prevent sleep while Now Playing is visible") },
                        leadingContent = {
                            Icon(Icons.Default.Brightness7, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.keepScreenOn,
                                onCheckedChange = viewModel::setKeepScreenOn
                            )
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
                            OutlinedButton(onClick = viewModel::syncNow) {
                                Text("Sync Now")
                            }
                        }
                    )
                }
            }

            // Storage section
            SettingsSectionHeader(title = "Storage")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Audio cache") },
                        supportingContent = {
                            val maxLabel = if (uiState.maxCacheSizeMb >= 1024) "${uiState.maxCacheSizeMb / 1024} GB" else "${uiState.maxCacheSizeMb} MB"
                            Text("${formatLargeFileSize(uiState.cacheSizeBytes)} / $maxLabel")
                        },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    CacheSizeDropdown(
                        currentSizeMb = uiState.maxCacheSizeMb,
                        onSizeSelected = viewModel::setAudioCacheSizeMb
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ReadAheadDropdown(
                        currentCount = uiState.cacheReadAhead,
                        onCountSelected = viewModel::setCacheReadAhead
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

            // Android Auto section
            SettingsSectionHeader(title = "Android Auto")
            AutoTabOrderSection(viewModel = viewModel)

            // Debug logs section
            SettingsSectionHeader(title = "Debug")
            DebugLogsSection(viewModel = viewModel)

            // About & Updates section
            SettingsSectionHeader(title = "About")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val context = LocalContext.current
                val versionName = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                    } catch (_: Exception) { "unknown" }
                }
                ListItem(
                    headlineContent = { Text("App version") },
                    supportingContent = { Text("Zonik v$versionName") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
            }
            UpdateSection(viewModel = viewModel)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// region Components

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

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
private fun DebugLogsSection(viewModel: SettingsViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val githubToken by viewModel.githubToken.collectAsState()
    val isUploading by viewModel.isUploadingLogs.collectAsState()
    val uploadUrl by viewModel.logUploadUrl.collectAsState()
    var showTokenDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            ListItem(
                headlineContent = { Text("Debug Logs") },
                supportingContent = { Text("Upload or copy logs for troubleshooting") },
                leadingContent = {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (githubToken == null) {
                ListItem(
                    headlineContent = {
                        OutlinedButton(onClick = { showTokenDialog = true }) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Set GitHub Token")
                        }
                    },
                    supportingContent = {
                        Text("Required for log upload. Create a token with 'gist' scope at GitHub Settings > Developer > Personal access tokens")
                    }
                )
            } else {
                ListItem(
                    headlineContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = viewModel::uploadLogs,
                                enabled = !isUploading
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isUploading) "Uploading..." else "Upload Logs")
                            }
                            TextButton(onClick = { showTokenDialog = true }) {
                                Text("Token")
                            }
                        }
                    }
                )

                if (uploadUrl != null) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Logs uploaded") },
                        supportingContent = {
                            Text(
                                text = uploadUrl!!,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Log URL", uploadUrl))
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                            }
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            val logs = DebugLog.getPersistedLogs()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Zonik Logs", logs))
                            copied = true
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (copied) "Copied!" else "Copy Logs")
                        }
                        OutlinedButton(onClick = {
                            DebugLog.clear()
                            copied = false
                        }) {
                            Text("Clear")
                        }
                    }
                }
            )
        }
    }

    if (showTokenDialog) {
        var tokenInput by remember { mutableStateOf(githubToken ?: "") }
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("GitHub Token") },
            text = {
                Column {
                    Text(
                        "Create a Personal Access Token at GitHub with 'gist' scope. Logs are uploaded as private gists.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setGithubToken(tokenInput)
                    showTokenDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AutoTabOrderSection(viewModel: SettingsViewModel) {
    val tabOrder by viewModel.autoTabOrder.collectAsState()
    val tabLabels = mapOf(
        "mix" to "Mix",
        "recent" to "Recently Added",
        "library" to "Library",
        "playlists" to "Playlists"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            ListItem(
                headlineContent = { Text("Tab order") },
                supportingContent = { Text("Reorder tabs shown in Android Auto") },
                leadingContent = {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            tabOrder.forEachIndexed { index, tabId ->
                ListItem(
                    headlineContent = { Text(tabLabels[tabId] ?: tabId) },
                    leadingContent = {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { viewModel.moveAutoTab(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { viewModel.moveAutoTab(index, index + 1) },
                                enabled = index < tabOrder.size - 1
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                            }
                        }
                    }
                )
                if (index < tabOrder.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Speed,
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
            Icon(icon, contentDescription = null)
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

@Composable
private fun ReadAheadDropdown(
    currentCount: Int,
    onCountSelected: (Int) -> Unit
) {
    val options = listOf(
        0 to "Off",
        1 to "1 track",
        2 to "2 tracks",
        3 to "3 tracks",
        5 to "5 tracks",
        10 to "10 tracks"
    )
    val currentLabel = options.find { it.first == currentCount }?.second ?: "$currentCount tracks"
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Read-ahead") },
        supportingContent = { Text("Pre-cache upcoming tracks") },
        leadingContent = {
            Icon(Icons.Default.FastForward, contentDescription = null)
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
                    options.forEach { (count, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onCountSelected(count)
                                expanded = false
                            },
                            trailingIcon = if (count == currentCount) {
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
private fun CacheSizeDropdown(
    currentSizeMb: Int,
    onSizeSelected: (Int) -> Unit
) {
    val options = listOf(
        0 to "Off",
        250 to "250 MB",
        500 to "500 MB",
        1024 to "1 GB",
        2048 to "2 GB",
        5120 to "5 GB",
        10240 to "10 GB"
    )
    val currentLabel = options.find { it.first == currentSizeMb }?.second ?: "${currentSizeMb} MB"
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Max cache size") },
        supportingContent = {
            Text(
                if (currentSizeMb == 0) "Caching disabled"
                else "$currentLabel (restart app to apply)"
            )
        },
        leadingContent = {
            Icon(Icons.Default.Sd, contentDescription = null)
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
                    options.forEach { (sizeMb, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSizeSelected(sizeMb)
                                expanded = false
                            },
                            trailingIcon = if (sizeMb == currentSizeMb) {
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
