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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.DebugLog
import com.zonik.app.data.api.*
import com.zonik.app.data.db.ZonikDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

// region State

data class DownloadsUiState(
    val searchQuery: String = "",
    val searchArtist: String = "",
    val searchResults: List<DownloadResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchStartTime: Long = 0L,
    val activeTransfers: List<TransferInfo> = emptyList(),
    val activeJobs: List<JobInfo> = emptyList(),
    val jobHistory: List<JobInfo> = emptyList(),
    val isLoadingJobs: Boolean = false,
    val error: String? = null,
    val selectedResults: Set<Int> = emptySet(),
    val expandedJobId: String? = null,
    val jobDetail: JobDetailResponse? = null,
    val isLoadingDetail: Boolean = false,
    val libraryTitleArtistPairs: Set<String> = emptySet(),
    val successMessage: String? = null
)

// endregion

// region ViewModel

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val zonikApi: ZonikApi,
    private val database: ZonikDatabase
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
        loadLibraryPairs()
    }

    private fun loadLibraryPairs() {
        viewModelScope.launch {
            try {
                val pairs = database.trackDao().getAllTitleArtistPairs()
                _uiState.update { it.copy(libraryTitleArtistPairs = pairs.toSet()) }
            } catch (_: Exception) {}
        }
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

        _uiState.update { it.copy(isSearching = true, searchStartTime = System.currentTimeMillis(), error = null, selectedResults = emptySet()) }

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
                val artist = _uiState.value.searchArtist.trim()
                val track = result.displayName
                DebugLog.d(TAG, "Triggering download: artist='$artist' track='$track' user=${result.username} file=${result.filename}")
                val response = zonikApi.triggerDownload(
                    DownloadTriggerRequest(
                        artist = artist,
                        track = track,
                        username = result.username,
                        filename = result.filename
                    )
                )
                DebugLog.d(TAG, "Trigger response: jobId=${response.jobId} message=${response.message}")
                _uiState.update { it.copy(error = null, successMessage = "Download started: $track") }
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
                        track = result.displayName,
                        username = result.username,
                        filename = result.filename
                    )
                }
                val response = zonikApi.bulkDownload(BulkDownloadRequest(tracks = tracks))
                DebugLog.d(TAG, "Bulk trigger response: jobId=${response.jobId} message=${response.message}")
                _uiState.update { it.copy(selectedResults = emptySet(), successMessage = "Downloading ${selected.size} tracks") }
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
                val response = zonikApi.getJobHistory()
                _uiState.update {
                    it.copy(
                        jobHistory = response.items,
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

    fun toggleJobDetail(jobId: String) {
        val current = _uiState.value.expandedJobId
        if (current == jobId) {
            _uiState.update { it.copy(expandedJobId = null, jobDetail = null) }
            return
        }
        _uiState.update { it.copy(expandedJobId = jobId, isLoadingDetail = true, jobDetail = null) }
        viewModelScope.launch {
            try {
                val detail = zonikApi.getJob(jobId)
                _uiState.update { it.copy(jobDetail = detail, isLoadingDetail = false) }
            } catch (e: Exception) {
                DebugLog.e(TAG, "Job detail load failed", e)
                _uiState.update { it.copy(isLoadingDetail = false) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null, successMessage = null) }
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
                windowInsets = WindowInsets(0),
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
            if (uiState.error != null) {
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss")
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(uiState.error!!)
                }
            } else if (uiState.successMessage != null) {
                LaunchedEffect(uiState.successMessage) {
                    delay(3000)
                    viewModel.dismissError()
                }
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(uiState.successMessage!!)
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
                    searchStartTime = uiState.searchStartTime,
                    selectedIndices = uiState.selectedResults,
                    onToggleSelection = viewModel::toggleSelection,
                    onDownloadSingle = { result -> viewModel.triggerDownload(result) },
                    libraryPairs = uiState.libraryTitleArtistPairs
                )

                DownloadsTab.ACTIVE -> ActiveTab(
                    transfers = uiState.activeTransfers,
                    activeJobs = uiState.activeJobs,
                    onCancel = viewModel::cancelTransfer
                )

                DownloadsTab.HISTORY -> HistoryTab(
                    history = uiState.jobHistory,
                    isLoading = uiState.isLoadingJobs,
                    expandedJobId = uiState.expandedJobId,
                    jobDetail = uiState.jobDetail,
                    isLoadingDetail = uiState.isLoadingDetail,
                    onToggleJob = viewModel::toggleJobDetail
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
    searchStartTime: Long,
    selectedIndices: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onDownloadSingle: (DownloadResult) -> Unit,
    libraryPairs: Set<String>
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Searching Soulseek network...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "This may take up to a minute",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (searchStartTime > 0L) {
                            var elapsed by remember { mutableIntStateOf(0) }
                            LaunchedEffect(searchStartTime) {
                                while (true) {
                                    elapsed = ((System.currentTimeMillis() - searchStartTime) / 1000).toInt()
                                    delay(1000L)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${elapsed}s elapsed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                        val inLibrary = remember(result.displayName, libraryPairs) {
                            val key = result.displayName.lowercase().trim() + "|||" + artist.lowercase().trim()
                            key in libraryPairs
                        }
                        ListItem(
                            headlineContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = result.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (inLibrary) {
                                        Surface(
                                            color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "IN LIBRARY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
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

private fun formatJobDate(isoDate: String): String {
    return try {
        val cleaned = isoDate.replace("T", " ").substringBefore(".")
        cleaned
    } catch (_: Exception) {
        isoDate
    }
}

private fun parseResultMessage(resultJson: String?): String? {
    if (resultJson == null) return null
    return try {
        val obj = JSONObject(resultJson)
        obj.optString("message", null as String?) ?: obj.optString("error", null as String?)
    } catch (_: Exception) {
        null
    }
}

private data class TrackEntry(
    val artist: String,
    val track: String,
    val status: String
)

private fun parseTracks(tracksJson: String?): List<TrackEntry> {
    if (tracksJson == null) return emptyList()
    return try {
        val arr = JSONArray(tracksJson)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            TrackEntry(
                artist = obj.optString("artist", ""),
                track = obj.optString("track", ""),
                status = obj.optString("status", "")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseLogEntries(logJson: String?): List<String> {
    if (logJson == null) return emptyList()
    return try {
        val arr = JSONArray(logJson)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun HistoryTab(
    history: List<JobInfo>,
    isLoading: Boolean,
    expandedJobId: String?,
    jobDetail: JobDetailResponse?,
    isLoadingDetail: Boolean,
    onToggleJob: (String) -> Unit
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
                    val isExpanded = expandedJobId == job.id
                    val headline = job.description?.takeIf { it.isNotBlank() }
                        ?: job.type.replace("_", " ").replaceFirstChar { it.uppercase() }

                    Column {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = headline,
                                    maxLines = if (isExpanded) 3 else 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = job.status.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = statusColor
                                        )
                                        if (job.progress != null && job.total != null && job.total > 0) {
                                            Text(
                                                text = "(${job.progress}/${job.total})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "\u00b7",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = job.type.replace("_", " "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    job.startedAt?.let { date ->
                                        Text(
                                            text = formatJobDate(date),
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
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                                )
                            },
                            modifier = Modifier.clickable { onToggleJob(job.id) }
                        )

                        AnimatedVisibility(visible = isExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isLoadingDetail) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (jobDetail != null && jobDetail.id == job.id) {
                                    // Duration
                                    if (jobDetail.startedAt != null && jobDetail.finishedAt != null) {
                                        Text(
                                            text = "${formatJobDate(jobDetail.startedAt)} \u2192 ${formatJobDate(jobDetail.finishedAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Result message
                                    parseResultMessage(jobDetail.result)?.let { msg ->
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (msg.contains("error", ignoreCase = true) || msg.contains("fail", ignoreCase = true))
                                                MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Tracks list
                                    val tracks = parseTracks(jobDetail.tracks)
                                    if (tracks.isNotEmpty()) {
                                        Text(
                                            text = "Tracks (${tracks.size}):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                tracks.forEach { entry ->
                                                    val trackStatusColor = when (entry.status.lowercase()) {
                                                        "completed", "complete", "imported" -> Color(0xFF2E7D32)
                                                        "failed", "error" -> MaterialTheme.colorScheme.error
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                    val trackStatusIcon = when (entry.status.lowercase()) {
                                                        "completed", "complete", "imported" -> "\u2713"
                                                        "failed", "error" -> "\u2717"
                                                        else -> "\u2022"
                                                    }
                                                    Row(
                                                        modifier = Modifier.padding(vertical = 2.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = trackStatusIcon,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = trackStatusColor
                                                        )
                                                        Text(
                                                            text = if (entry.artist.isNotBlank()) "${entry.artist} \u2014 ${entry.track}" else entry.track,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (entry.status.isNotBlank()) {
                                                            Text(
                                                                text = entry.status,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = trackStatusColor
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Log entries
                                    val logEntries = parseLogEntries(jobDetail.log)
                                    if (logEntries.isNotEmpty()) {
                                        Text(
                                            text = "Log:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                logEntries.forEach { line ->
                                                    Text(
                                                        text = line,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (tracks.isEmpty() && logEntries.isEmpty() && parseResultMessage(jobDetail.result) == null) {
                                        Text(
                                            text = "No additional details",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

// endregion
