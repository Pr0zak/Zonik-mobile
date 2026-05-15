package com.zonik.wear.ui.screens.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onPaired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is PairingState.Paired) onPaired()
    }

    ScreenScaffold(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 24.dp),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is PairingState.EnterUrl -> EnterUrl(s, viewModel)
                is PairingState.Requesting -> Requesting()
                is PairingState.Pending -> Pending(s, viewModel)
                is PairingState.Paired -> PairedIndicator(s)
            }
        }
    }
}

@Composable
private fun EnterUrl(state: PairingState.EnterUrl, viewModel: PairingViewModel) {
    var manualEntryOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(state.url) }

    if (!manualEntryOpen) {
        Icon(
            imageVector = Icons.Default.Watch,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Pair with phone",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Open Zonik on your phone → Settings → Wear OS → Send",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "or enter URL manually",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { manualEntryOpen = true },
            textAlign = TextAlign.Center,
        )
        return
    }

    Text(
        text = "Server URL",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(8.dp),
    )
    if (state.error != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            viewModel.updateUrl(draft)
            viewModel.requestCode()
        },
        enabled = draft.isNotBlank(),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Icon(Icons.Default.Check, contentDescription = "Continue")
    }
}

@Composable
private fun Requesting() {
    CircularProgressIndicator(
        modifier = Modifier.size(36.dp),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "Requesting code…",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Pending(state: PairingState.Pending, viewModel: PairingViewModel) {
    Text(
        text = state.code,
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Open <server>/pair in a browser",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    FilledTonalButton(onClick = { viewModel.cancelAndStartOver() }) {
        Text("Start over")
    }
}

@Composable
private fun PairedIndicator(state: PairingState.Paired) {
    Icon(
        Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(32.dp),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Paired as ${state.username}",
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
