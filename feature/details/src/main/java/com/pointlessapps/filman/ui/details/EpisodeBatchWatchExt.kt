package com.pointlessapps.filman.ui.details

import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MovieItem

internal fun DetailedMedia.collectEpisodesUpTo(
    currentSeason: Int,
    currentEpisode: Int,
): List<MovieItem> {
    val details = baseItem
    val seasons = details.seasons ?: return emptyList()

    return buildList {
        for ((sIndex, season) in seasons.withIndex()) {
            val firstEpRef = season.episodes.firstOrNull()?.let { MediaRef.parse(it.url, isTvShowHint = true) }
            val seasonNum = firstEpRef?.season ?: (sIndex + 1)
            if (seasonNum > currentSeason) continue

            val episodes = if (seasonNum == currentSeason) {
                season.episodes.take(currentEpisode)
            } else {
                season.episodes
            }

            for ((eIndex, ep) in episodes.withIndex()) {
                val nextEp = season.episodes.getOrNull(eIndex + 1)
                    ?: seasons.getOrNull(sIndex + 1)?.episodes?.firstOrNull()

                val epRef = MediaRef.parse(ep.url, isTvShowHint = true)
                val epNumber = epRef?.episode ?: (eIndex + 1)

                add(
                    MovieItem(
                        url = ep.url,
                        titlePl = ep.title,
                        posterUrl = details.posterUrl,
                        seriesUrl = details.url,
                        seasonNumber = seasonNum,
                        episodeNumber = epNumber,
                        episodeTitle = ep.title,
                        nextEpisodeUrl = nextEp?.url,
                        isTvShow = true,
                    ),
                )
            }
        }
    }
}
