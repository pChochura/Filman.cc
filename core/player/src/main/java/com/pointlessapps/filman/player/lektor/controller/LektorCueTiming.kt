package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.player.lektor.model.SubtitleCue

internal object LektorCueTiming {
    fun position(positionMs: Long, offsetMs: Int): Long = positionMs - offsetMs.toLong()

    fun isDue(cue: SubtitleCue, positionMs: Long, offsetMs: Int): Boolean {
        val adjustedPosition = position(positionMs, offsetMs)
        return adjustedPosition >= cue.startMs && adjustedPosition <= cue.endMs + 300L
    }
}
