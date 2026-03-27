package com.zonik.app.ui.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zonik.app.ui.theme.ZonikColors

fun Context.isTvDevice(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        || packageManager.hasSystemFeature("com.google.android.tv")
        || android.app.UiModeManager::class.java.let {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
            uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        }
}

@Composable
fun isTv(): Boolean {
    val context = LocalContext.current
    return remember { context.isTvDevice() }
}

@Composable
fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(8.dp)
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    this.onFocusChanged { isFocused = it.isFocused }
        .then(
            if (isFocused) Modifier.border(2.dp, ZonikColors.gold, shape)
            else Modifier
        )
}
