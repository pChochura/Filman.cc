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
        val season: Int? = null,
        val episode: Int? = null,
        val seriesTitle: String? = null,
        val episodeTitle: String? = null,
    ) : ProgressItem() {
        val seasonEpisode: String?
            get() = if (season != null && episode != null) {
                "S${season}E$episode"
            } else {
                ""
            }

        val displayTitle: String
            get() = if (seriesTitle != null && season != null && episode != null) {
                if (episodeTitle != null) {
                    "$seriesTitle - $episodeTitle"
                } else {
                    "$seriesTitle - S${season}E$episode"
                }
            } else {
                titlePl
            }
    }
}
