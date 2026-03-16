package com.zonik.app.ui.util

fun formatDuration(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    return formatDuration(totalSeconds)
}

fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0) return ""
    val mb = bytes / 1_048_576.0
    return "%.1f MB".format(mb)
}
