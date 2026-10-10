package com.pointlessapps.filman.lite.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** [location] is either a remote URL or an absolute local file path. */
data class PlaybackSource(
    val title: String,
    val location: String,
    val headers: Map<String, String> = emptyMap(),
)

@Composable
expect fun VideoPlayer(source: PlaybackSource, modifier: Modifier = Modifier)
