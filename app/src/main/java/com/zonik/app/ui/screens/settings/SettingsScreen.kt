package com.zonik.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.zonik.app.data.DebugLog
import com.zonik.app.ui.theme.ZonikShapes
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                windowInsets = WindowInsets.statusBars
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
                    .padding(horizontal = 20.dp),
                shape = ZonikShapes.cardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
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
                    .padding(horizontal = 20.dp),
                shape = ZonikShapes.cardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A))
            ) {
                Column {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Server URL") },
                        supportingContent = {
                            Text(
                                text = uiState.serverUrl.ifEmpty { "Not connected" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                    .padding(horizontal = 20.dp),
                shape = ZonikShapes.cardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
            ) {
                Column {
                    BitrateDropdown(
                        label = "Wi-Fi max bitrate",
                        icon = Icons.Default.Wifi,
                        currentBitrate = uiState.wifiBitrate,
                        onBitrateSelected = viewModel::setWifiBitrate,
                        wrapIcon = true
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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

            // Equalizer section
            SettingsSectionHeader(title = "Equalizer")
            EqualizerSection(viewModel = viewModel, uiState = uiState)

            // Sync section
            SettingsSectionHeader(title = "Sync")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = ZonikShapes.cardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
            ) {
                Column {
                    SyncIntervalDropdown(
                        currentMinutes = uiState.syncIntervalMinutes,
                        onIntervalSelected = viewModel::setSyncInterval
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            OutlinedButton(onClick = viewModel::syncNow) {
                                Text("Sync Now")
                            }
                        }
                    )
                }
            }

            // Cache & Offline section
            SettingsSectionHeader(title = "Cache & Offline")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = ZonikShapes.cardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A))
            ) {
                Column {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Audio cache") },
                        overlineContent = { Text("Caches tracks for offline playback and slow connections") },
                        supportingContent = {
                            Column {
                                val maxLabel = if (uiState.maxCacheSizeMb >= 1024) "${uiState.maxCacheSizeMb / 1024} GB" else "${uiState.maxCacheSizeMb} MB"
                                Text("${formatLargeFileSize(uiState.cacheSizeBytes)} / $maxLabel")
                                if (uiState.maxCacheSizeMb > 0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { (uiState.cacheSizeBytes.toFloat() / (uiState.maxCacheSizeMb * 1024L * 1024L).toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Adaptive bitrate") },
                        supportingContent = { Text("Auto-reduce quality on slow connections") },
                        leadingContent = {
                            Icon(Icons.Default.Speed, contentDescription = null)
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.adaptiveBitrate,
                                onCheckedChange = viewModel::setAdaptiveBitrate
                            )
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            OutlinedButton(onClick = viewModel::clearCache) {
                                Text("Clear Audio Cache")
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    CoverArtCacheSizeDropdown(
                        currentSizeMb = uiState.coverArtCacheSizeMb,
                        onSizeSelected = viewModel::setCoverArtCacheSizeMb
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val coverArtContext = LocalContext.current
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            OutlinedButton(onClick = { viewModel.clearCoverArtCache(coverArtContext) }) {
                                Text("Clear Cover Art Cache")
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OfflineCacheSection(viewModel = viewModel)

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
                    .padding(horizontal = 20.dp),
                shape = ZonikShapes.cardShape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
            ) {
                val context = LocalContext.current
                val versionName = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                    } catch (_: Exception) { "unknown" }
                }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("App version") },
                    supportingContent = { Text("Zonik v$versionName") },
                    leadingContent = {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("GitHub") },
                    supportingContent = { Text("Source code & releases") },
                    leadingContent = {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    trailingContent = {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Pr0zak/Zonik-mobile"))
                        context.startActivity(intent)
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp
            ),
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
            .padding(horizontal = 20.dp),
        shape = ZonikShapes.cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
    ) {
        Column {
            val update = availableUpdate
            if (update != null) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
    val isUploadingToServer by viewModel.isUploadingLogsToServer.collectAsState()
    val serverUploadResult by viewModel.serverUploadResult.collectAsState()
    var showTokenDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = ZonikShapes.cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A))
    ) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Debug Logs") },
                supportingContent = { Text("Upload or copy logs for troubleshooting") },
                leadingContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Upload to Zonik server (no extra config needed)
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Button(
                        onClick = viewModel::uploadLogsToServer,
                        enabled = !isUploadingToServer
                    ) {
                        if (isUploadingToServer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isUploadingToServer) "Uploading..." else "Upload to Server")
                    }
                }
            )

            if (serverUploadResult != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val isSuccess = serverUploadResult!!.startsWith("Uploaded")
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(if (isSuccess) "Logs uploaded" else "Upload failed") },
                    supportingContent = { Text(serverUploadResult!!) },
                    leadingContent = {
                        Icon(
                            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (githubToken == null) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
            .padding(horizontal = 20.dp),
        shape = ZonikShapes.cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1824))
    ) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Tab order") },
                supportingContent = { Text("Reorder tabs shown in Android Auto") },
                leadingContent = {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null)
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            tabOrder.forEachIndexed { index, tabId ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
private fun EqualizerSection(viewModel: SettingsViewModel, uiState: SettingsUiState) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = ZonikShapes.cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A))
    ) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Waveform Seek Bar") },
                supportingContent = { Text("Show track waveform on seek bar") },
                leadingContent = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = uiState.visualizerEnabled,
                        onCheckedChange = viewModel::setVisualizerEnabled
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Equalizer") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = uiState.eqEnabled,
                        onCheckedChange = viewModel::setEqEnabled
                    )
                }
            )

            if (uiState.eqEnabled) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Preset dropdown
                val presets = listOf("Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock")
                val presetLabel = if (uiState.eqPreset < 0) "Custom" else presets.getOrElse(uiState.eqPreset) { "Preset ${uiState.eqPreset}" }
                var expanded by remember { mutableStateOf(false) }

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Preset") },
                    supportingContent = { Text(presetLabel) },
                    leadingContent = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    trailingContent = {
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(presetLabel)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                presets.forEachIndexed { index, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { viewModel.setEqPreset(index); expanded = false },
                                        trailingIcon = if (index == uiState.eqPreset) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                )

                // Custom band sliders (show when preset is Custom)
                if (uiState.eqPreset < 0) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val bands = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
                    val levels = uiState.eqBandLevels?.split(",")?.mapNotNull { it.toShortOrNull() }
                        ?: List(5) { 0.toShort() }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Custom EQ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        bands.forEachIndexed { index, label ->
                            val level = levels.getOrElse(index) { 0 }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                                Slider(
                                    value = level.toFloat(),
                                    onValueChange = { viewModel.setEqBandLevel(index, it.toInt().toShort()) },
                                    valueRange = -1500f..1500f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${level / 100}dB", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                            }
                        }
                    }
                }

                // System EQ button
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        OutlinedButton(onClick = {
                            try {
                                val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                                intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                }
                            } catch (_: Exception) {}
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("System Equalizer")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun OfflineCacheSection(viewModel: SettingsViewModel) {
    val offlineCacheEnabled by viewModel.offlineCacheEnabled.collectAsState()
    val autoCacheQueue by viewModel.autoCacheQueue.collectAsState()
    val autoCacheFavorites by viewModel.autoCacheFavorites.collectAsState()
    val offlineStorageLimitMb by viewModel.offlineStorageLimitMb.collectAsState()
    val offlineStorageUsedBytes by viewModel.offlineStorageUsedBytes.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = ZonikShapes.cardShape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C2A))
    ) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Offline Caching") },
                supportingContent = { Text("Download tracks for offline playback") },
                leadingContent = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = offlineCacheEnabled,
                        onCheckedChange = viewModel::setOfflineCacheEnabled
                    )
                }
            )

            if (offlineCacheEnabled) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Auto-cache queue") },
                    supportingContent = { Text("Download queued tracks in background") },
                    trailingContent = {
                        Switch(checked = autoCacheQueue, onCheckedChange = viewModel::setAutoCacheQueue)
                    }
                )

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Auto-cache favorites") },
                    supportingContent = { Text("Download starred tracks after sync") },
                    trailingContent = {
                        Switch(checked = autoCacheFavorites, onCheckedChange = viewModel::setAutoCacheFavorites)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Storage limit dropdown
                var expanded by remember { mutableStateOf(false) }
                val limitOptions = listOf(2048, 5120, 10240, 20480, 51200, 0)
                val limitLabel = when {
                    offlineStorageLimitMb == 0 -> "No limit"
                    offlineStorageLimitMb < 1024 -> "${offlineStorageLimitMb} MB"
                    else -> "${"%.0f".format(offlineStorageLimitMb / 1024.0)} GB"
                }
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text("Storage limit") },
                    supportingContent = {
                        val usedMb = offlineStorageUsedBytes / (1024 * 1024)
                        Text("${usedMb} MB / $limitLabel used")
                    },
                    trailingContent = {
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(limitLabel)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                limitOptions.forEach { mb ->
                                    val label = when {
                                        mb == 0 -> "No limit"
                                        mb < 1024 -> "$mb MB"
                                        else -> "${"%.0f".format(mb / 1024.0)} GB"
                                    }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { viewModel.setOfflineStorageLimitMb(mb); expanded = false },
                                        trailingIcon = if (mb == offlineStorageLimitMb) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                )

                // Progress bar
                if (offlineStorageUsedBytes > 0) {
                    val progress = (offlineStorageUsedBytes.toFloat() / (offlineStorageLimitMb * 1024 * 1024)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .height(4.dp),
                        color = if (progress > 0.9f) Color(0xFFE57373) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        TextButton(
                            onClick = { viewModel.clearOfflineCache() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE57373))
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear Offline Cache")
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
        text = title.uppercase(),
        style = MaterialTheme.typography.titleSmall.copy(
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp)
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text("Sync interval") },
        supportingContent = { Text(currentLabel) },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
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
    onBitrateSelected: (Int) -> Unit,
    wrapIcon: Boolean = false
) {
    val options = listOf(
        0 to "Original (no limit)",
        320 to "320 kbps",
        256 to "256 kbps",
        192 to "192 kbps",
        128 to "128 kbps",
        64 to "64 kbps (low data)"
    )
    val currentLabel = options.find { it.first == currentBitrate }?.second ?: "Original (no limit)"
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(label) },
        supportingContent = { Text(currentLabel) },
        leadingContent = {
            if (wrapIcon) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                Icon(icon, contentDescription = null)
            }
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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

@Composable
private fun CoverArtCacheSizeDropdown(
    currentSizeMb: Int,
    onSizeSelected: (Int) -> Unit
) {
    val options = listOf(
        100 to "100 MB",
        250 to "250 MB",
        500 to "500 MB",
        1024 to "1 GB"
    )
    val currentLabel = options.find { it.first == currentSizeMb }?.second ?: "${currentSizeMb} MB"
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text("Cover art cache size") },
        supportingContent = { Text("$currentLabel (restart app to apply)") },
        leadingContent = {
            Icon(Icons.Default.Image, contentDescription = null)
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
