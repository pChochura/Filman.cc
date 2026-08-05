package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD

@Immutable
data class EpisodeItem(
    val titlePl: String,
    val titleEn: String? = null,
    val url: String,
    val posterUrl: String,
    val progress: Float,
    val season: Int? = null,
    val episode: Int? = null,
    val nextEpisodeUrl: String? = null,
) {
    val isFinished: Boolean
        get() = progress >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
}
