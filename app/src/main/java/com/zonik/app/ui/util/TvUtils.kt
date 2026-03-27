package com.zonik.app.ui.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
