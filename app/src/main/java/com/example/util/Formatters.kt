package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

fun formatResolution(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "Unknown"
    val shortSide = minOf(width, height)
    val label = when {
        shortSide >= 2160 -> "4K"
        shortSide >= 1440 -> "1440p"
        shortSide >= 1080 -> "1080p"
        shortSide >= 720 -> "720p"
        else -> "${shortSide}p"
    }
    return "$label ($width×$height)"
}

fun formatDate(timestampMs: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    return formatter.format(Date(timestampMs))
}
