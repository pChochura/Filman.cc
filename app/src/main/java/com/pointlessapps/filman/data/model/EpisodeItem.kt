package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD

@Immutable
data class EpisodeItem(
    val titlePl: String,
    val titleEn: String? = null,
    val url: String,
    val posterUrl: String,
    val progress: ProgressItem?,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val progressPercentage: Float
        get() = when (progress) {
            is ProgressItem.InProgress -> progress.progressPercentage
            is ProgressItem.Watched -> 1f
            else -> 0f
        }

    val isFinished: Boolean
        get() = progressPercentage >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
}
