package com.pointlessapps.filman.ui.player.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class NextEpisodeButtonUIState(
    val isVisible: Boolean = false,
    val isSecondaryPhase: Boolean = false,
    val isTimerRunning: Boolean = false,
    val timerDurationMs: Long = 0L,
    val isPastSoftOffset: Boolean = false,
)
