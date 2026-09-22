package com.pointlessapps.filman.ui.player

import androidx.compose.runtime.Immutable

@Immutable
data class PlayerAudioTrack(
    val id: String,
    val label: String,
    val language: String? = null,
    val isSelected: Boolean = false,
)
