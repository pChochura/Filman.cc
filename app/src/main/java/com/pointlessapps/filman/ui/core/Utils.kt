package com.pointlessapps.filman.ui.core

import kotlin.math.sign

internal fun String.titlecase() = lowercase().replaceFirstChar { it.uppercase() }

internal fun Long.parseDuration(): String {
    val hours = this.unsigned / (1000 * 60 * 60)
    val minutes = (this.unsigned / (1000 * 60)) % 60
    val seconds = (this.unsigned / 1000) % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(
            hours.coerceIn(0, 24),
            minutes.coerceIn(0, 60),
            seconds.coerceIn(0, 60),
        )
    } else {
        "%02d:%02d".format(minutes.coerceIn(0, 60), seconds.coerceIn(0, 60))
    }
}

private val Long.unsigned: Long
    get() = if (this.sign >= 0L) this else -this
