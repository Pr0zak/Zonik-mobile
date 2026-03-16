package com.zonik.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.repository.LibraryRepository
import com.zonik.app.data.repository.SyncManager
import com.zonik.app.data.repository.SyncState
import com.zonik.app.media.PlaybackManager
import com.zonik.app.model.Album
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    libraryRepository: LibraryRepository,
    private val playbackManager: PlaybackManager,
    private val syncManager: SyncManager
) : ViewModel() {

    val recentAlbums = libraryRepository.getRecentAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncState = syncManager.syncState

    fun syncNow() {
        viewModelScope.launch { syncManager.fullSync() }
    }

    fun shuffleMix() {
        // TODO: Implement shuffle mix
    }

    fun trueRandom() {
        // TODO: Implement true random
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val recentAlbums by viewModel.recentAlbums.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zonik") },
                actions = {
                    if (syncState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    IconButton(
                        onClick = viewModel::syncNow,
                        enabled = !syncState.isSyncing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Sync status banner
            SyncBanner(syncState = syncState)

            // Quick actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = viewModel::shuffleMix,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Mix")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle Mix")
                }
                FilledTonalButton(
                    onClick = viewModel::trueRandom,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Casino, contentDescription = "True Random")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("True Random")
                }
            }

            // Recently Added
            Text(
                text = "Recently Added",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (recentAlbums.isEmpty() && !syncState.isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No albums yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::syncNow) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Library")
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentAlbums, key = { it.id }) { album ->
                        AlbumCard(album = album)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SyncBanner(syncState: SyncState) {
    if (syncState.isSyncing) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = syncState.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }

    if (syncState.error != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = syncState.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }

    if (!syncState.isSyncing && syncState.error == null && syncState.lastSyncResult != null) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = syncState.lastSyncResult,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.width(150.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {}

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
