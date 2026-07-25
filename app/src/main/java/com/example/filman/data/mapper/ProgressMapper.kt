package com.example.filman.data.mapper

import com.example.filman.data.model.MovieItem
import com.example.filman.data.model.ProgressItem

internal fun MovieItem.toInProgress(
    progressPercentage: Float,
    progressMs: Long,
): ProgressItem.InProgress =
    ProgressItem.InProgress(
        progressPercentage = progressPercentage,
        url = url,
        parentUrl = seriesUrl ?: url,
        progressMs = progressMs,
        posterUrl = posterUrl,
        titlePl = titlePl,
        season = seasonNumber,
        episode = episodeNumber,
        seriesTitle = if (seasonNumber != null) titlePl else null,
        episodeTitle = episodeTitle,
    )

internal fun MovieItem.toWatched(): ProgressItem.Watched =
    ProgressItem.Watched(
        url = url,
        parentUrl = seriesUrl ?: url,
        posterUrl = posterUrl,
        titlePl = titlePl,
        season = seasonNumber,
        episode = episodeNumber,
        seriesTitle = if (seasonNumber != null) titlePl else null,
        episodeTitle = episodeTitle,
    )
