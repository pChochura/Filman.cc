package com.example.filman.ui.core

internal fun String.titlecase() = lowercase().replaceFirstChar { it.uppercase() }

internal fun Long.parseDuration(): String {
    val hours = this / (1000 * 60 * 60)
    val minutes = (this / (1000 * 60)) % 60
    val seconds = (this / 1000) % 60

    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes.coerceIn(0, 60), seconds.coerceIn(0, 60))
    } else {
        "%02d:%02d".format(minutes.coerceIn(0, 60), seconds.coerceIn(0, 60))
    }
}
