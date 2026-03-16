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

fun formatLargeDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0m"
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

fun formatLargeFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = bytes / 1_048_576.0
    return "%.1f MB".format(mb)
}
