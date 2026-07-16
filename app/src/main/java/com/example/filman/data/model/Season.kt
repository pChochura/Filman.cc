package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class Season(
    val name: String,
    val episodes: List<EpisodeLink>,
)
