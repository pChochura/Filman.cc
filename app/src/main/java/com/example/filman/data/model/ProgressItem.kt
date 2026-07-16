package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class ProgressItem(
    val url: String,
    val titlePl: String,
    val posterUrl: String,
    val progressMs: Long,
    val durationMs: Long,
    val seriesTitle: String? = null,
    val seriesUrl: String? = null,
) {
    val progressPercentage: Float
        get() = if (durationMs > 0) (progressMs.toFloat() / durationMs.toFloat()) else 0f

    val seasonEpisode: String?
        get() = seasonEpisodeRegex.find(titlePl)?.let {
            "S${it.groupValues[1]}E${it.groupValues[2]}"
        }
}

private val seasonEpisodeRegex = Regex("(?i)s(\\d+)e(\\d+)")
