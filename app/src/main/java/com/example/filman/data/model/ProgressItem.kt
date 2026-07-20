package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed class ProgressItem {
    abstract val url: String
    abstract val parentUrl: String?
    abstract val progressPercentage: Float

    @Serializable
    @Immutable
    data class Watched(
        override val url: String,
        override val parentUrl: String?,
    ) : ProgressItem() {
        override val progressPercentage = 1f
    }

    @Serializable
    @Immutable
    data class InProgress(
        override val progressPercentage: Float,
        override val url: String,
        override val parentUrl: String?,
        val progressMs: Long,
        val posterUrl: String,
        val titlePl: String = "",
    ) : ProgressItem() {
        val seasonEpisode: String?
            get() = seasonEpisodeRegex.find(titlePl)?.let {
                "S${it.groupValues[1]}E${it.groupValues[2]}"
            }
    }
}

private val seasonEpisodeRegex = Regex("(?i)s(\\d+)e(\\d+)")
