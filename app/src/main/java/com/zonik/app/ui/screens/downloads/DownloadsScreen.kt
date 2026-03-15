package com.zonik.app.ui.screens.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// region Data classes

data class DownloadSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val format: String,
    val bitrate: Int,
    val fileSize: Long
)

enum class DownloadStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED
}

data class DownloadQueueItem(
    val id: String,
    val title: String,
    val artist: String,
    val status: DownloadStatus,
    val progress: Float = 0f
)

// endregion

// region ViewModel

data class DownloadsUiState(
    val searchQuery: String = "",
    val searchResults: List<DownloadSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedResultIds: Set<String> = emptySet(),
    val queue: List<DownloadQueueItem> = stubQueue()
)

private fun stubQueue(): List<DownloadQueueItem> = listOf(
    DownloadQueueItem("q1", "Echoes", "Pink Floyd", DownloadStatus.COMPLETED),
    DownloadQueueItem("q2", "Comfortably Numb", "Pink Floyd", DownloadStatus.DOWNLOADING, 0.65f),
    DownloadQueueItem("q3", "Time", "Pink Floyd", DownloadStatus.PENDING),
    DownloadQueueItem("q4", "Money", "Pink Floyd", DownloadStatus.FAILED)
)

@HiltViewModel
class DownloadsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        // Stub: generate fake results based on query
        _uiState.update { it.copy(isSearching = true) }

        val stubResults = listOf(
            DownloadSearchResult("r1", "$query - Track 1", "Various Artists", "FLAC", 1411, 35_000_000),
            DownloadSearchResult("r2", "$query - Track 2", "Unknown Artist", "MP3", 320, 9_500_000),
            DownloadSearchResult("r3", "$query (Live)", "Various Artists", "FLAC", 1411, 42_000_000),
            DownloadSearchResult("r4", "$query (Remix)", "DJ Unknown", "MP3", 256, 8_200_000),
            DownloadSearchResult("r5", "$query - Acoustic", "Singer", "FLAC", 1411, 28_000_000)
        )
        _uiState.update {
            it.copy(
                searchResults = stubResults,
                isSearching = false,
                selectedResultIds = emptySet()
            )
        }
    }

    fun toggleResultSelection(resultId: String) {
        _uiState.update { state ->
            val newSelection = state.selectedResultIds.toMutableSet()
            if (resultId in newSelection) newSelection.remove(resultId) else newSelection.add(resultId)
            state.copy(selectedResultIds = newSelection)
        }
    }

    fun downloadSingle(resultId: String) {
        // Stub: add to queue
        val result = _uiState.value.searchResults.find { it.id == resultId } ?: return
        val item = DownloadQueueItem(
            id = result.id,
            title = result.title,
            artist = result.artist,
            status = DownloadStatus.PENDING
        )
        _uiState.update { it.copy(queue = it.queue + item) }
    }

    fun downloadSelected() {
        val selected = _uiState.value.selectedResultIds
        val results = _uiState.value.searchResults.filter { it.id in selected }
        val items = results.map { result ->
            DownloadQueueItem(
                id = result.id,
                title = result.title,
                artist = result.artist,
                status = DownloadStatus.PENDING
            )
        }
        _uiState.update {
            it.copy(queue = it.queue + items, selectedResultIds = emptySet())
        }
    }

    fun retryDownload(itemId: String) {
        _uiState.update { state ->
            state.copy(
                queue = state.queue.map {
                    if (it.id == itemId) it.copy(status = DownloadStatus.PENDING, progress = 0f) else it
                }
            )
        }
    }
}

// endregion

// region Screen

private enum class DownloadsTab(val label: String) {
    SEARCH("Search"),
    QUEUE("Queue")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: DownloadsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = DownloadsTab.entries

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Downloads") })
        },
        floatingActionButton = {
            if (selectedTab == 0 && uiState.selectedResultIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::downloadSelected,
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    text = { Text("Download Selected (${uiState.selectedResultIds.size})") }
                )
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
                    onQueryChanged = viewModel::onSearchQueryChanged,
                    onSearch = viewModel::search,
                    results = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    selectedIds = uiState.selectedResultIds,
                    onToggleSelection = viewModel::toggleResultSelection,
                    onDownloadSingle = viewModel::downloadSingle
                )
                DownloadsTab.QUEUE -> QueueTab(
                    queue = uiState.queue,
                    onRetry = viewModel::retryDownload
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
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    results: List<DownloadSearchResult>,
    isSearching: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onDownloadSingle: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search Soulseek...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )
            FilledTonalButton(onClick = onSearch, enabled = query.isNotBlank()) {
                Text("Search")
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
                val isMultiSelect = selectedIds.isNotEmpty()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(results, key = { it.id }) { result ->
                        SearchResultItem(
                            result = result,
                            isSelected = result.id in selectedIds,
                            isMultiSelect = isMultiSelect,
                            onToggleSelection = { onToggleSelection(result.id) },
                            onDownload = { onDownloadSingle(result.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: DownloadSearchResult,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onToggleSelection: () -> Unit,
    onDownload: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = result.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = result.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = result.format,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.height(24.dp)
                )
                Text(
                    text = "${result.bitrate}k",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatFileSize(result.fileSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = if (isMultiSelect) {
            {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() }
                )
            }
        } else null,
        trailingContent = {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
        },
        modifier = Modifier.clickable(onClick = onToggleSelection)
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

// endregion

// region Queue Tab

@Composable
private fun QueueTab(
    queue: List<DownloadQueueItem>,
    onRetry: (String) -> Unit
) {
    if (queue.isEmpty()) {
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
                    text = "Download queue is empty",
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
        items(queue, key = { it.id }) { item ->
            QueueItemRow(item = item, onRetry = { onRetry(item.id) })
        }
    }
}

@Composable
private fun QueueItemRow(
    item: DownloadQueueItem,
    onRetry: () -> Unit
) {
    Column {
        ListItem(
            headlineContent = {
                Text(
                    text = item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column {
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (item.status) {
                            DownloadStatus.PENDING -> "Pending"
                            DownloadStatus.DOWNLOADING -> "Downloading..."
                            DownloadStatus.COMPLETED -> "Completed"
                            DownloadStatus.FAILED -> "Failed"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (item.status) {
                            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.small,
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (item.status) {
                                DownloadStatus.PENDING -> Icons.Default.HourglassEmpty
                                DownloadStatus.DOWNLOADING -> Icons.Default.Download
                                DownloadStatus.COMPLETED -> Icons.Default.Check
                                DownloadStatus.FAILED -> Icons.Default.ErrorOutline
                            },
                            contentDescription = null,
                            tint = when (item.status) {
                                DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
                                DownloadStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            },
            trailingContent = {
                if (item.status == DownloadStatus.FAILED) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        AnimatedVisibility(visible = item.status == DownloadStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

// endregion
