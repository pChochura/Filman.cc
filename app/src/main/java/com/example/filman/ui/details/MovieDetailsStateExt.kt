package com.example.filman.ui.details

import com.example.filman.R
import com.example.filman.data.model.EpisodeItem
import com.example.filman.data.model.EpisodeLink
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.model.Season
import com.example.filman.ui.components.sections.TabRowSectionItem

internal val MovieDetailsState.tabs: List<TabRowSectionItem>
    get() = buildList {
        if (mediaDetails?.baseItem?.seasons != null) {
            add(
                TabRowSectionItem(
                    title = R.string.details_episodes,
                    id = TabRowItemId.Episodes.id,
                ),
            )
        }

        if (mediaDetails?.similarMovies?.isNotEmpty() == true) {
            add(
                TabRowSectionItem(
                    title = R.string.details_similar,
                    id = TabRowItemId.Similar.id,
                ),
            )
        }

        add(
            TabRowSectionItem(
                title = R.string.details_about,
                id = TabRowItemId.Details.id,
            ),
        )
    }

internal fun MovieDetailsState.getSeasonEpisodes(season: Season) = season.episodes.map { episode ->
    val progress = progressMap[episode.url]
    EpisodeItem(
        titlePl = episode.title,
        titleEn = null,
        url = episode.url,
        posterUrl = mediaDetails?.baseItem?.posterUrl.orEmpty(),
        progress = progress,
    )
}

internal val MovieDetailsState.watchButtonState: WatchButtonState
    get() {
        val baseItem = mediaDetails?.baseItem ?: return WatchButtonState.Default
        val isSeries = baseItem.seasons != null

        val mostRecent = progressList.firstOrNull { progress ->
            progress.parentUrl == baseItem.url
        }

        if (!isSeries) {
            return when {
                mostRecent != null && mostRecent.progressPercentage < 0.95f ->
                    WatchButtonState.Continue

                mostRecent != null -> WatchButtonState.WatchAgain
                else -> WatchButtonState.Default
            }
        }

        val flatEpisodesUrls = baseItem.seasons.flatMapIndexed { index, season ->
            season.episodes.map { index + 1 to it.url }
        }
        if (mostRecent != null) {
            val currentIndex = flatEpisodesUrls.indexOfFirst { it.second == mostRecent.url }

            val isFinished = when (mostRecent) {
                is ProgressItem.Watched -> true
                is ProgressItem.InProgress -> mostRecent.progressPercentage > 0.95f
            }

            if (isFinished) {
                flatEpisodesUrls.getOrNull(currentIndex + 1)?.let {
                    return WatchButtonState.WatchNextEpisode(
                        season = it.first.toString(), episode = (currentIndex + 1).toString(),
                    )
                }
            } else {
                flatEpisodesUrls.getOrNull(currentIndex)?.let {
                    return WatchButtonState.ContinueEpisode(
                        season = it.first.toString(), episode = currentIndex.toString(),
                    )
                }
            }
        }

        return WatchButtonState.Default
    }

internal fun MovieDetailsState.getWatchButtonUrl(): String? {
    val baseItem = mediaDetails?.baseItem ?: return null
    val isSeries = baseItem.seasons != null

    if (!isSeries) return baseItem.url

    val mostRecent = progressList.firstOrNull { progress ->
        progress.parentUrl == baseItem.url
    }

    val flatEpisodesUrls = baseItem.seasons.flatMap { it.episodes.map(EpisodeLink::url) }
    if (mostRecent != null) {
        val currentIndex = flatEpisodesUrls.indexOf(mostRecent.url)

        val isFinished = when (mostRecent) {
            is ProgressItem.Watched -> true
            is ProgressItem.InProgress -> mostRecent.progressPercentage > 0.95f
        }

        if (isFinished) {
            flatEpisodesUrls.getOrNull(currentIndex + 1)?.let {
                return it
            }
        } else {
            flatEpisodesUrls.getOrNull(currentIndex)?.let {
                return it
            }
        }
    }

    return flatEpisodesUrls.firstOrNull() ?: baseItem.url
}
