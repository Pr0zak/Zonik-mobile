package com.zonik.app.ui.screens.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.DebugLog
import com.zonik.app.data.api.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// region State

data class DownloadsUiState(
    val searchQuery: String = "",
    val searchArtist: String = "",
    val searchResults: List<DownloadResult> = emptyList(),
    val isSearching: Boolean = false,
    val activeTransfers: List<TransferInfo> = emptyList(),
    val activeJobs: List<JobInfo> = emptyList(),
    val jobHistory: List<JobInfo> = emptyList(),
    val isLoadingJobs: Boolean = false,
    val error: String? = null,
    val selectedResults: Set<Int> = emptySet()
)

// endregion

// region ViewModel

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val zonikApi: ZonikApi
) : ViewModel() {

    companion object {
        private const val TAG = "DownloadsVM"
    }

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
        loadHistory()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(3000)
                try {
                    val statusResponse = zonikApi.getDownloadStatus()
                    val activeJobs = zonikApi.getActiveJobs()
                    _uiState.update {
                        it.copy(
                            activeTransfers = statusResponse.transfers,
                            activeJobs = activeJobs
                        )
                    }
                } catch (_: Exception) {
                    // Silent refresh failure
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchArtistChanged(artist: String) {
        _uiState.update { it.copy(searchArtist = artist) }
    }

    fun search() {
        val state = _uiState.value
        if (state.searchQuery.isBlank() && state.searchArtist.isBlank()) return

        _uiState.update { it.copy(isSearching = true, error = null, selectedResults = emptySet()) }

        viewModelScope.launch {
            try {
                DebugLog.d(TAG, "Searching: artist='${state.searchArtist}' track='${state.searchQuery}'")
                val response = zonikApi.searchDownloads(
                    DownloadSearchRequest(
                        artist = state.searchArtist.trim(),
                        track = state.searchQuery.trim()
                    )
                )
                DebugLog.d(TAG, "Search returned ${response.results.size} results")
                _uiState.update {
                    it.copy(
                        searchResults = response.results,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Search failed", e)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "Search failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun triggerDownload(result: DownloadResult) {
        viewModelScope.launch {
            try {
                DebugLog.d(TAG, "Triggering download: ${result.displayName}")
                zonikApi.triggerDownload(
                    DownloadTriggerRequest(
                        artist = _uiState.value.searchArtist.trim(),
                        track = _uiState.value.searchQuery.trim(),
                        username = result.username,
                        filename = result.filename
                    )
                )
                refreshStatus()
            } catch (e: Exception) {
                DebugLog.e(TAG, "Download trigger failed", e)
                _uiState.update { it.copy(error = "Download failed: ${e.message}") }
            }
        }
    }

    fun triggerBulkDownload() {
        val state = _uiState.value
        val selected = state.selectedResults.mapNotNull { idx ->
            state.searchResults.getOrNull(idx)
        }
        if (selected.isEmpty()) return

        viewModelScope.launch {
            try {
                DebugLog.d(TAG, "Bulk downloading ${selected.size} tracks")
                val tracks = selected.map { result ->
                    BulkDownloadTrack(
                        artist = state.searchArtist.trim(),
                        track = result.displayName
                    )
                }
                zonikApi.bulkDownload(BulkDownloadRequest(tracks = tracks))
                _uiState.update { it.copy(selectedResults = emptySet()) }
                refreshStatus()
            } catch (e: Exception) {
                DebugLog.e(TAG, "Bulk download failed", e)
                _uiState.update { it.copy(error = "Bulk download failed: ${e.message}") }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            try {
                val statusResponse = zonikApi.getDownloadStatus()
                val activeJobs = zonikApi.getActiveJobs()
                _uiState.update {
                    it.copy(
                        activeTransfers = statusResponse.transfers,
                        activeJobs = activeJobs,
                        error = null
                    )
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Status refresh failed", e)
                _uiState.update { it.copy(error = "Status refresh failed: ${e.message}") }
            }
        }
    }

    fun loadHistory() {
        _uiState.update { it.copy(isLoadingJobs = true) }
        viewModelScope.launch {
            try {
                val jobs = zonikApi.getJobHistory()
                _uiState.update {
                    it.copy(
                        jobHistory = jobs,
                        isLoadingJobs = false
                    )
                }
            } catch (e: Exception) {
                DebugLog.e(TAG, "History load failed", e)
                _uiState.update {
                    it.copy(
                        isLoadingJobs = false,
                        error = "History load failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleSelection(index: Int) {
        _uiState.update { state ->
            val newSelection = state.selectedResults.toMutableSet()
            if (index in newSelection) newSelection.remove(index) else newSelection.add(index)
            state.copy(selectedResults = newSelection)
        }
    }

    fun cancelTransfer(transfer: TransferInfo) {
        viewModelScope.launch {
            try {
                DebugLog.d(TAG, "Cancelling transfer: ${transfer.displayName}")
                zonikApi.cancelTransfer(
                    CancelTransferRequest(
                        username = transfer.username,
                        filename = transfer.filename
                    )
                )
                refreshStatus()
            } catch (e: Exception) {
                DebugLog.e(TAG, "Cancel transfer failed", e)
                _uiState.update { it.copy(error = "Cancel failed: ${e.message}") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

// endregion

// region Screen

private enum class DownloadsTab(val label: String) {
    SEARCH("Search"),
    ACTIVE("Active"),
    HISTORY("History")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = DownloadsTab.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = { viewModel.refreshStatus() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    if (selectedTab == 2) {
                        IconButton(onClick = { viewModel.loadHistory() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0 && uiState.selectedResults.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::triggerBulkDownload,
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    text = { Text("Download Selected (${uiState.selectedResults.size})") }
                )
            }
        },
        snackbarHost = {
            uiState.error?.let { error ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss")
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (tabs[selectedTab]) {
                DownloadsTab.SEARCH -> SearchTab(
                    query = uiState.searchQuery,
                    artist = uiState.searchArtist,
                    onQueryChanged = viewModel::onSearchQueryChanged,
                    onArtistChanged = viewModel::onSearchArtistChanged,
                    onSearch = viewModel::search,
                    results = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    selectedIndices = uiState.selectedResults,
                    onToggleSelection = viewModel::toggleSelection,
                    onDownloadSingle = { result -> viewModel.triggerDownload(result) }
                )

                DownloadsTab.ACTIVE -> ActiveTab(
                    transfers = uiState.activeTransfers,
                    activeJobs = uiState.activeJobs,
                    onCancel = viewModel::cancelTransfer
                )

                DownloadsTab.HISTORY -> HistoryTab(
                    history = uiState.jobHistory,
                    isLoading = uiState.isLoadingJobs
                )
            }
        }
    }
}

// endregion

// region Search Tab

@Composable
private fun SearchTab(
    query: String,
    artist: String,
    onQueryChanged: (String) -> Unit,
    onArtistChanged: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<DownloadResult>,
    isSearching: Boolean,
    selectedIndices: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onDownloadSingle: (DownloadResult) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = artist,
                onValueChange = onArtistChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Artist") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                trailingIcon = {
                    if (artist.isNotEmpty()) {
                        IconButton(onClick = { onArtistChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Track title") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                FilledTonalButton(
                    onClick = onSearch,
                    enabled = query.isNotBlank() || artist.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search")
                }
            }
        }

        when {
            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            results.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search for music on Soulseek",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(results) { index, result ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = result.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "${result.username} \u00b7 ${result.format} \u00b7 ${result.sizeMb} \u00b7 ${result.bitRate ?: "?"}kbps",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = index in selectedIndices,
                                    onCheckedChange = { onToggleSelection(index) }
                                )
                            },
                            modifier = Modifier.clickable { onDownloadSingle(result) }
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region Active Tab

@Composable
private fun ActiveTab(
    transfers: List<TransferInfo>,
    activeJobs: List<JobInfo>,
    onCancel: (TransferInfo) -> Unit
) {
    if (transfers.isEmpty() && activeJobs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No active downloads",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        if (transfers.isNotEmpty()) {
            item {
                Text(
                    text = "Transfers",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(transfers, key = { "${it.username}/${it.filename}" }) { transfer ->
                TransferItem(transfer = transfer, onCancel = { onCancel(transfer) })
            }
        }

        if (activeJobs.isNotEmpty()) {
            item {
                Text(
                    text = "Active Jobs",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(activeJobs, key = { it.id }) { job ->
                ListItem(
                    headlineContent = { Text(job.type) },
                    supportingContent = {
                        Text(
                            text = "${job.status} ${job.progress?.let { p -> job.total?.let { t -> "($p/$t)" } } ?: ""}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Work, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
private fun TransferItem(
    transfer: TransferInfo,
    onCancel: () -> Unit
) {
    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = transfer.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TransferStateBadge(state = transfer.state)
                    if (transfer.state.equals("Transferring", ignoreCase = true)) {
                        Text(
                            text = buildString {
                                if (transfer.speedMbps.isNotEmpty()) append(transfer.speedMbps)
                                append(" \u00b7 ${transfer.progress.toInt()}%")
                                transfer.etaSeconds?.let { append(" \u00b7 ETA ${it}s") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    transfer.error?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            trailingContent = {
                val isActive = transfer.state.equals("Queued", ignoreCase = true) ||
                        transfer.state.equals("Transferring", ignoreCase = true)
                if (isActive) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        AnimatedVisibility(visible = transfer.state.equals("Transferring", ignoreCase = true)) {
            LinearProgressIndicator(
                progress = { transfer.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun TransferStateBadge(state: String) {
    val (containerColor, contentColor) = when (state.lowercase()) {
        "queued" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "transferring" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "completed" -> Color(0xFF2E7D32) to Color.White
        "failed", "denied" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    SuggestionChip(
        onClick = {},
        label = { Text(state, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        ),
        modifier = Modifier.height(24.dp)
    )
}

// endregion

// region History Tab

@Composable
private fun HistoryTab(
    history: List<JobInfo>,
    isLoading: Boolean
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        history.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No download history",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(history, key = { it.id }) { job ->
                    val statusColor = when (job.status.lowercase()) {
                        "completed", "complete" -> Color(0xFF2E7D32)
                        "failed", "error" -> MaterialTheme.colorScheme.error
                        "running", "in_progress" -> Color(0xFFED6C02)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = job.type.replace("_", " ").replaceFirstChar { it.uppercase() },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = job.status.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                                job.createdAt?.let { date ->
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = MaterialTheme.shapes.small,
                                color = statusColor.copy(alpha = 0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (job.status.lowercase()) {
                                            "completed", "complete" -> Icons.Default.Check
                                            "failed", "error" -> Icons.Default.ErrorOutline
                                            "running", "in_progress" -> Icons.Default.Sync
                                            else -> Icons.Default.Schedule
                                        },
                                        contentDescription = null,
                                        tint = statusColor
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

// endregion
