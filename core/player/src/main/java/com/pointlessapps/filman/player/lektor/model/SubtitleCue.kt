package com.pointlessapps.filman.player.lektor.model

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}
