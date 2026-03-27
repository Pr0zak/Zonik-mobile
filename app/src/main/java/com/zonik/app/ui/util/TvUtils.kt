package com.zonik.app.ui.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

fun Context.isTvDevice(): Boolean {
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

@Composable
fun isTv(): Boolean {
    val context = LocalContext.current
    return remember { context.isTvDevice() }
}
