package com.zonik.wear.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.zonik.wear.BuildConfig
import com.zonik.wear.WearApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun WearSettingsScreen(
    onRePair: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as WearApp
    val config by app.settings.serverConfig.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { event ->
                scope.launch { listState.scrollBy(event.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 24.dp),
    ) {
        item {
            ListHeader { Text("Settings", color = MaterialTheme.colors.primary) }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = config?.url ?: "(not paired)",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = config?.username?.let { "as $it" } ?: "",
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }

        item {
            Chip(
                onClick = {
                    scope.launch {
                        app.settings.clear()
                        onRePair()
                    }
                },
                label = { Text("Re-pair device") },
                icon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(20.dp)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Chip(
                onClick = {
                    scope.launch {
                        val (deleted, total) = withContext(Dispatchers.IO) {
                            clearStreamCache(ctx.cacheDir)
                        }
                        cacheClearedMessage = "Freed ${deleted}/${total} files"
                    }
                },
                label = { Text("Clear stream cache") },
                secondaryLabel = cacheClearedMessage?.let { { Text(it) } },
                icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Zonik Wear v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.caption3,
                color = MaterialTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

private fun clearStreamCache(cacheDir: File): Pair<Int, Int> {
    val dir = File(cacheDir, "exoplayer_audio_cache")
    if (!dir.exists()) return 0 to 0
    var total = 0
    var deleted = 0
    dir.walkTopDown().forEach { f ->
        if (f.isFile) {
            total += 1
            if (f.delete()) deleted += 1
        }
    }
    return deleted to total
}
