package com.zonik.wear.ui.screens.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onPaired: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is PairingState.Paired) onPaired()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 22.dp),
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

@Composable
private fun EnterUrl(state: PairingState.EnterUrl, viewModel: PairingViewModel) {
    var manualEntryOpen by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(state.url) }

    if (!manualEntryOpen) {
        // Default path — wait for the phone app to push ServerConfig over
        // the Wear Data Layer. WearNavHost's serverConfig observer routes
        // us out automatically as soon as PairingDataListener saves it.
        Text(
            text = "Zonik",
            style = MaterialTheme.typography.title2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Open Zonik on your phone → Settings → Wear OS → Send",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "or enter URL manually",
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { manualEntryOpen = true },
            textAlign = TextAlign.Center,
        )
        return
    }

    // Manual fallback — kept for setups without a paired phone, or if
    // BT is off and the user wants to use the code flow.
    Text(
        text = "Server URL",
        style = MaterialTheme.typography.caption1,
        color = MaterialTheme.colors.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        textStyle = TextStyle(
            color = MaterialTheme.colors.onSurface,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(MaterialTheme.colors.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
    )
    if (state.error != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.error,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.error,
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
    ) {
        Icon(Icons.Default.Check, contentDescription = "Continue", tint = Color.White)
    }
}

@Composable
private fun Requesting() {
    Spacer(Modifier.height(20.dp))
    CircularProgressIndicator()
    Spacer(Modifier.height(8.dp))
    Text(
        "Requesting code…",
        style = MaterialTheme.typography.caption1,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Pending(state: PairingState.Pending, viewModel: PairingViewModel) {
    Text(
        text = state.code,
        style = MaterialTheme.typography.display1,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Open <server>/pair in a browser and enter this code",
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { viewModel.cancelAndStartOver() },
        colors = ButtonDefaults.secondaryButtonColors(),
    ) {
        Icon(Icons.Default.Refresh, contentDescription = "Start over")
    }
}

@Composable
private fun PairedIndicator(state: PairingState.Paired) {
    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green)
    Spacer(Modifier.height(4.dp))
    Text(
        "Paired as ${state.username}",
        style = MaterialTheme.typography.caption1,
        textAlign = TextAlign.Center,
    )
}
