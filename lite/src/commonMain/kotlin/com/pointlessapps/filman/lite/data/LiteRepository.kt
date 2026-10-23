package com.pointlessapps.filman.lite.data

import kotlinx.coroutines.delay

class LiteRepository internal constructor(
    private val api: LiteApi,
    private val pollDelayMs: Long = POLL_DELAY_MS,
) {
    suspend fun search(query: String): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        return api.search(query.trim()).results.map {
            MediaItem(
                id = it.id,
                title = it.title,
                overview = it.overview,
                posterUrl = it.posterPath?.let { path -> IMAGE_BASE_URL + path },
                isTvShow = it.mediaType == "TV_SHOW",
            )
        }
    }

    suspend fun seasons(show: MediaItem): List<Season> =
        api.tvShow(show.id).seasons
            .filter { it.episodes.isNotEmpty() }
            .sortedBy { it.seasonNumber }
            .map { season ->
                Season(
                    number = season.seasonNumber,
                    name = season.name.ifBlank { "Season ${season.seasonNumber}" },
                    episodes = season.episodes.sortedBy { it.episodeNumber }
                        .map { Episode(it.seasonNumber, it.episodeNumber, it.name) },
                )
            }

    /** Link extraction runs on the server in the background, so keep asking until it settles. */
    suspend fun sources(item: MediaItem, episode: Episode? = null): List<StreamSource> {
        var response: LinksResponseDto? = null
        repeat(MAX_POLL_ATTEMPTS) { attempt ->
            val current = if (item.isTvShow) {
                val ep = episode ?: throw LiteException("Choose an episode first")
                api.tvShowLinks(item.id, ep.season, ep.episode)
            } else {
                api.movieLinks(item.id)
            }
            response = current
            if (current.requiresFilmanAuth || current.status == "NEEDS_AUTHORIZATION" && !current.requiresCloudflare) {
                throw AuthRequiredException(current.message ?: "Log in to Filman to load sources")
            }
            if (current.requiresCloudflare || current.pageRequest != null) {
                throw LiteException(current.message ?: "The source requires verification, which isn't supported in Lite")
            }
            val settled = current.status != "PENDING" && current.status != "PARTIAL"
            if (settled || (current.links.isNotEmpty() && attempt >= MIN_POLL_ATTEMPTS_WITH_LINKS)) {
                return current.toSources()
            }
            delay(pollDelayMs)
        }
        return response?.toSources().orEmpty()
    }

    private fun LinksResponseDto.toSources(): List<StreamSource> {
        if (status == "ERROR" && links.isEmpty()) throw LiteException(message ?: "Couldn't find any sources")
        return links.filter { it.displayMode != "WEBVIEW" }.map {
            StreamSource(it.url, it.quality, it.language, it.serverName.ifEmpty { it.sourceWebsite }, it.headers)
        }
    }

    companion object {
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
        private const val MAX_POLL_ATTEMPTS = 30
        private const val MIN_POLL_ATTEMPTS_WITH_LINKS = 3
        private const val POLL_DELAY_MS = 2000L
    }
}
