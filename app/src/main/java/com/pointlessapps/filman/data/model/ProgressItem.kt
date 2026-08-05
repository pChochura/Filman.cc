package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed class ProgressItem {
    abstract val url: String
    abstract val parentUrl: String?
    abstract val progressPercentage: Float
    abstract val posterUrl: String
    abstract val titlePl: String
    abstract val season: Int?
    abstract val episode: Int?
    abstract val seriesTitle: String?
    abstract val episodeTitle: String?
    abstract val hasNextEpisode: Boolean

    val seasonEpisode: String?
        get() = if (season != null && episode != null) {
            "S${season}E$episode"
        } else {
            null
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

    @Serializable
    @Immutable
    data class Watched(
        override val url: String,
        override val parentUrl: String?,
        override val posterUrl: String = "",
        override val titlePl: String = "",
        override val season: Int? = null,
        override val episode: Int? = null,
        override val seriesTitle: String? = null,
        override val episodeTitle: String? = null,
        override val hasNextEpisode: Boolean = false,
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
        override val posterUrl: String = "",
        override val titlePl: String = "",
        override val season: Int? = null,
        override val episode: Int? = null,
        override val seriesTitle: String? = null,
        override val episodeTitle: String? = null,
        override val hasNextEpisode: Boolean = false,
    ) : ProgressItem()

    @Serializable
    @Immutable
    data class NextEpisode(
        override val url: String,
        override val parentUrl: String?,
        override val posterUrl: String = "",
        override val titlePl: String = "",
        override val season: Int? = null,
        override val episode: Int? = null,
        override val seriesTitle: String? = null,
        override val episodeTitle: String? = null,
        override val hasNextEpisode: Boolean = false,
    ) : ProgressItem() {
        override val progressPercentage = 0f
    }
}
