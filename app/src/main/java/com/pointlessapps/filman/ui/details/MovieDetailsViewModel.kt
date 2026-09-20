package com.pointlessapps.filman.ui.details

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.data.scraper.TmdbClient
import com.pointlessapps.filman.data.scraper.VideoUrlResolver
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.TabRowSectionItem
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.details.MovieDetailsEffect.NavigateToActor
import com.pointlessapps.filman.ui.details.MovieDetailsEffect.NavigateToPlayer
import com.pointlessapps.filman.utils.findBestMatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlin.time.Duration.Companion.milliseconds

internal sealed interface MovieDetailsEvent : FilmanEvent {
    data class OpenActorDetails(
        val url: String,
    ) : MovieDetailsEvent

    data class LoadDetails(
        val request: DetailsRequest,
    ) : MovieDetailsEvent

    data object ToggleFavorite : MovieDetailsEvent

    data class PlayItem(
        val url: String,
    ) : MovieDetailsEvent

    data class WatchTrailer(
        val url: String,
    ) : MovieDetailsEvent

    data class TabChanged(
        val tab: TabRowSectionItem,
    ) : MovieDetailsEvent

    data object LoadMoreRecommendations : MovieDetailsEvent
}

@Immutable
internal data class MovieDetailsState(
    override val shared: SharedState = SharedState(),
    val mediaDetails: DetailedMedia? = null,
    val isFavorite: Boolean = false,
    val progressList: List<ProgressItem> = emptyList(),
    val selectedTabId: Int = TabRowItemId.Similar.id,
    val trailerUrl: String? = null,
    val tmdbRecommendations: List<MovieItem> = emptyList(),
    val tmdbRecommendationsPage: Int = 1,
    val tmdbRecommendationsHasMore: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
) : StateWithShared<MovieDetailsState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal enum class TabRowItemId(
    val id: Int,
) {
    Episodes(0),
    Recommended(1),
    Similar(2),
    Details(3),
}

internal sealed interface WatchButtonState {
    val url: String

    data class Default(
        override val url: String = "",
    ) : WatchButtonState

    data object Unavailable : WatchButtonState {
        override val url: String = ""
    }

    data class WatchAgain(
        override val url: String,
    ) : WatchButtonState

    data class Continue(
        override val url: String,
    ) : WatchButtonState

    data class WatchNextEpisode(
        val season: String,
        val episode: String,
        override val url: String,
    ) : WatchButtonState

    data class ContinueEpisode(
        val season: String,
        val episode: String,
        override val url: String,
    ) : WatchButtonState
}

internal sealed interface MovieDetailsEffect {
    data object NavigateToAuth : MovieDetailsEffect

    data class NavigateToPlayer(
        val url: String,
    ) : MovieDetailsEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
    ) : MovieDetailsEffect

    data class NavigateToActor(
        val url: String,
    ) : MovieDetailsEffect
}

internal class MovieDetailsViewModel(
    private val scraper: FilmanScraper,
    private val videoUrlResolver: VideoUrlResolver,
    private val tmdbClient: TmdbClient,
    favoritesManager: FavoritesManager,
    progressManager: ProgressManager,
) : BaseViewModel<MovieDetailsState, MovieDetailsEvent, MovieDetailsEffect>(
    initialState = MovieDetailsState(),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
) {
    init {
        launchHandled {
            progressManager.progressItemsFlow.collect { progressList ->
                updateState { state ->
                    state.copy(
                        progressList = progressList,
                    )
                }
            }
        }
    }

    override fun getAuthErrorEffect(): MovieDetailsEffect = MovieDetailsEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): MovieDetailsEffect = MovieDetailsEffect.NavigateToDetails(request ?: DetailsRequest.Url(url))

    override fun handleBaseEvent(event: BaseEvent) {
        if (event !is BaseEvent.MarkPreviousAsWatched) return super.handleBaseEvent(event)

        val details = currentState.mediaDetails?.baseItem ?: return
        val seasons = details.seasons ?: return

        val currentSeason = event.movie.seasonNumber ?: return
        val currentEpisode = event.movie.episodeNumber ?: return

        val itemsToSave =
            buildList {
                for ((sIndex, season) in seasons.withIndex()) {
                    val seasonNum = sIndex + 1
                    if (seasonNum > currentSeason) continue

                    val episodes =
                        if (seasonNum == currentSeason) {
                            season.episodes.take(currentEpisode)
                        } else {
                            season.episodes
                        }

                    for ((eIndex, ep) in episodes.withIndex()) {
                        val nextEp =
                            season.episodes.getOrNull(eIndex + 1)
                                ?: seasons.getOrNull(sIndex + 1)?.episodes?.firstOrNull()

                        val epMovie =
                            MovieItem(
                                url = ep.url,
                                titlePl = ep.title,
                                posterUrl = details.posterUrl,
                                seriesUrl = details.url,
                                seasonNumber = seasonNum,
                                episodeNumber = eIndex + 1,
                                episodeTitle = ep.title,
                                nextEpisodeUrl = nextEp?.url,
                            )
                        add(epMovie)
                    }
                }
            }

        progressManager?.markAsWatched(itemsToSave)
    }

    override fun handleEvent(event: MovieDetailsEvent) {
        when (event) {
            is MovieDetailsEvent.OpenActorDetails -> {
                sendEffect(NavigateToActor(event.url))
            }

            is MovieDetailsEvent.LoadDetails -> {
                loadDetails(event.request)
            }

            is MovieDetailsEvent.ToggleFavorite -> {
                toggleFavorite()
            }

            is MovieDetailsEvent.PlayItem -> {
                sendEffect(NavigateToPlayer(event.url))
            }

            is MovieDetailsEvent.WatchTrailer -> {
                sendEffect(
                    NavigateToPlayer(event.url),
                )
            }

            is MovieDetailsEvent.TabChanged -> {
                updateState { it.copy(selectedTabId = event.tab.id) }
            }

            is MovieDetailsEvent.LoadMoreRecommendations -> {
                loadMoreRecommendations()
            }
        }
    }

    private fun loadDetails(request: DetailsRequest) {
        when (request) {
            is DetailsRequest.Search -> {
                updateState {
                    it.copy(
                        shared =
                            it.shared.copy(
                                isLoading = true,
                                errorMessage = null,
                            ),
                    )
                }

                val title = request.title
                val year = request.year
                val isTvShow = request.isTvShow

                launchHandled(
                    onError = {
                        updateSharedState { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage =
                                    it.message?.let(TextValue::DynamicString)
                                        ?: TextValue.StringResource(R.string.error_unknown),
                            )
                        }
                        handleError(it)
                    },
                ) {
                    var resolvedUrl = ""
                    scraper.searchMovies(
                        query = title,
                        prioritySource = MediaSource.FILMAN,
                    ).takeWhile { resolvedUrl.isEmpty() }
                        .collect { searchResult ->
                            val items = if (isTvShow) searchResult.tvShows else searchResult.movies
                            val matchingItem = items.findBestMatch(title, year)
                            if (matchingItem != null && resolvedUrl.isEmpty()) {
                                resolvedUrl = matchingItem.url
                            }
                        }

                    if (resolvedUrl.isNotEmpty()) {
                        loadDetails(DetailsRequest.Url(resolvedUrl))
                    } else {
                        updateSharedState {
                            it.copy(
                                isLoading = false,
                                errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                            )
                        }
                    }
                }
            }

            is DetailsRequest.GroupUrls -> {
                updateState {
                    it.copy(
                        shared =
                            it.shared.copy(
                                isLoading = true,
                                errorMessage = null,
                            ),
                        mediaDetails = null,
                        isFavorite = false,
                        trailerUrl = null,
                    )
                }

                launchHandled(
                    onError = {
                        updateSharedState { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage =
                                    it.message?.let(TextValue::DynamicString)
                                        ?: TextValue.StringResource(R.string.error_unknown),
                            )
                        }
                        handleError(it)
                    },
                ) {
                    val urls = request.urls
                    if (urls.isEmpty()) {
                        updateSharedState {
                            it.copy(
                                isLoading = false,
                                errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                            )
                        }
                        return@launchHandled
                    }

                    var mergedDetails: DetailedMedia? = null

                    for (url in urls) {
                        val details = scraper.getMediaDetails(url) ?: continue

                        if (mergedDetails == null) {
                            mergedDetails = details

                            val isFavorite = favoritesManager?.isFavorite(url) == true

                            videoUrlResolver.prefetch(url, mergedDetails)

                            updateState {
                                val nextState =
                                    it.copy(
                                        shared = it.shared.copy(isLoading = false),
                                        mediaDetails = mergedDetails,
                                        isFavorite = isFavorite,
                                    )
                                nextState.copy(
                                    selectedTabId = nextState.tabs.firstOrNull()?.id
                                        ?: TabRowItemId.Similar.id,
                                )
                            }

                            // Load TMDB Recommendations and Trailer based on the first successful item
                            val title =
                                mergedDetails.baseItem.titleEn ?: mergedDetails.baseItem.titlePl
                            val year = mergedDetails.metaInfo?.year
                            val isTvShow = mergedDetails.seasonsNumber != null

                            val tmdbId = tmdbClient.getTmdbId(title, year, isTvShow)
                            if (tmdbId != null) {
                                val (tmdbMovies, totalPages) = tmdbClient.getRecommendations(
                                    tmdbId,
                                    isTvShow,
                                )
                                val recommendations =
                                    tmdbMovies.map { tmdbMovie ->
                                        MovieItem(
                                            url = "tmdb_${tmdbMovie.id}",
                                            titlePl = tmdbMovie.title,
                                            posterUrl = tmdbMovie.posterUrl,
                                            year = tmdbMovie.releaseYear,
                                            isTvShow = tmdbMovie.isTvShow,
                                            detailsRequest = DetailsRequest.Search(
                                                title = tmdbMovie.title,
                                                year = tmdbMovie.releaseYear,
                                                isTvShow = tmdbMovie.isTvShow,
                                            ),
                                        )
                                    }
                                if (recommendations.isNotEmpty()) {
                                    updateState {
                                        val nextState =
                                            it.copy(
                                                tmdbRecommendations = recommendations,
                                                tmdbRecommendationsPage = 1,
                                                tmdbRecommendationsHasMore = totalPages > 1,
                                            )
                                        nextState.copy(
                                            selectedTabId = if (it.selectedTabId == TabRowItemId.Similar.id && it.mediaDetails?.similarMovies.isNullOrEmpty()) {
                                                nextState.tabs.firstOrNull()?.id
                                                    ?: TabRowItemId.Similar.id
                                            } else {
                                                it.selectedTabId
                                            },
                                        )
                                    }
                                }
                            }

                            val trailerUrl = tmdbClient.getTrailerUrl(
                                title = title,
                                year = year,
                                isTvShow = isTvShow,
                            )
                            if (trailerUrl != null) {
                                updateState { it.copy(trailerUrl = trailerUrl) }
                            }
                        } else {
                            delay(1000.milliseconds)
                            mergedDetails = mergeMediaDetails(mergedDetails, details)
                            updateState {
                                it.copy(mediaDetails = mergedDetails)
                            }
                        }
                    }

                    if (mergedDetails == null) {
                        updateSharedState {
                            it.copy(
                                isLoading = false,
                                errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                            )
                        }
                    } else if (mergedDetails.embeds.isEmpty() && mergedDetails.baseItem.seasons == null) {
                        val tmdbEmbeds =
                            tmdbClient.getEmbeds(
                                title = mergedDetails.baseItem.titleEn
                                    ?: mergedDetails.baseItem.titlePl,
                                year = mergedDetails.metaInfo?.year,
                            )
                        if (tmdbEmbeds.isNotEmpty()) {
                            mergedDetails =
                                mergedDetails.copy(embeds = mergedDetails.embeds + tmdbEmbeds)
                            updateState {
                                it.copy(mediaDetails = mergedDetails)
                            }
                        }
                    }
                }
            }

            is DetailsRequest.Url -> {
                val url = request.url
                val current = currentState
                if (current.mediaDetails?.baseItem?.url == url && !current.shared.isLoading) {
                    return
                }

                updateState {
                    it.copy(
                        shared =
                            it.shared.copy(
                                isLoading = true,
                                errorMessage = null,
                            ),
                        mediaDetails = null,
                        isFavorite = false,
                        trailerUrl = null,
                    )
                }

                launchHandled(
                    onError = {
                        updateSharedState { state ->
                            state.copy(
                                isLoading = false,
                                errorMessage =
                                    it.message?.let(TextValue::DynamicString)
                                        ?: TextValue.StringResource(R.string.error_unknown),
                            )
                        }
                        handleError(it)
                    },
                ) {
                    val details = scraper.getMediaDetails(url)
                    val isFavorite = favoritesManager?.isFavorite(url) == true

                    val finalDetails =
                        if (details != null && details.embeds.isEmpty() && details.baseItem.seasons == null) {
                            val tmdbEmbeds =
                                tmdbClient.getEmbeds(
                                    title = details.baseItem.titleEn ?: details.baseItem.titlePl,
                                    year = details.metaInfo?.year,
                                )
                            if (tmdbEmbeds.isNotEmpty()) {
                                details.copy(embeds = tmdbEmbeds)
                            } else {
                                details
                            }
                        } else {
                            details
                        }

                    finalDetails?.let { videoUrlResolver.prefetch(url, it) }

                    updateState {
                        val nextState =
                            it.copy(
                                shared = it.shared.copy(isLoading = false),
                                mediaDetails = finalDetails,
                                isFavorite = isFavorite,
                            )
                        nextState.copy(
                            selectedTabId = nextState.tabs.firstOrNull()?.id
                                ?: TabRowItemId.Similar.id,
                        )
                    }

                    if (details != null) {
                        val title = details.baseItem.titleEn ?: details.baseItem.titlePl
                        val year = details.metaInfo?.year
                        val isTvShow = details.seasonsNumber != null

                        val tmdbId = tmdbClient.getTmdbId(title, year, isTvShow)

                        if (tmdbId != null) {
                            val (tmdbMovies, totalPages) = tmdbClient.getRecommendations(
                                tmdbId,
                                isTvShow,
                            )
                            val recommendations =
                                tmdbMovies.map { tmdbMovie ->
                                    MovieItem(
                                        url = "tmdb_${tmdbMovie.id}",
                                        titlePl = tmdbMovie.title,
                                        posterUrl = tmdbMovie.posterUrl,
                                        year = tmdbMovie.releaseYear,
                                        isTvShow = tmdbMovie.isTvShow,
                                        detailsRequest = DetailsRequest.Search(
                                            title = tmdbMovie.title,
                                            year = tmdbMovie.releaseYear,
                                            isTvShow = tmdbMovie.isTvShow,
                                        ),
                                    )
                                }
                            if (recommendations.isNotEmpty()) {
                                updateState {
                                    val nextState = it.copy(
                                        tmdbRecommendations = recommendations,
                                        tmdbRecommendationsPage = 1,
                                        tmdbRecommendationsHasMore = totalPages > 1,
                                    )
                                    nextState.copy(
                                        selectedTabId = if (it.selectedTabId == TabRowItemId.Similar.id && it.mediaDetails?.similarMovies.isNullOrEmpty()) {
                                            nextState.tabs.firstOrNull()?.id
                                                ?: TabRowItemId.Similar.id
                                        } else {
                                            it.selectedTabId
                                        },
                                    )
                                }
                            }
                        }

                        val trailerUrl =
                            tmdbClient.getTrailerUrl(
                                title = title,
                                year = year,
                                isTvShow = isTvShow,
                            )
                        if (trailerUrl != null) {
                            updateState { it.copy(trailerUrl = trailerUrl) }
                        }
                    }
                }
            }
        }
    }

    private fun toggleFavorite() {
        val current = currentState
        val details = current.mediaDetails?.baseItem ?: return

        if (current.isFavorite) {
            favoritesManager?.removeFavorite(details.url)
            updateState { it.copy(isFavorite = false) }
        } else {
            val targetTitle = details.titlePl.substringBefore(" - ").trim()
            val movieToSave =
                MovieItem(
                    url = details.seriesUrl ?: details.url,
                    titlePl = targetTitle,
                    titleEn = details.titleEn,
                    posterUrl = details.posterUrl,
                    backgroundUrl = details.backgroundUrl,
                    source = details.source,
                    year = details.year,
                    filmanRating = details.filmanRating,
                    imdbRating = details.imdbRating,
                )
            favoritesManager?.addFavorite(movieToSave)
            updateState { it.copy(isFavorite = true) }
        }
    }

    private fun loadMoreRecommendations() {
        val current = currentState
        if (current.isLoadingMoreRecommendations || !current.tmdbRecommendationsHasMore) return
        val details = current.mediaDetails ?: return
        val title = details.baseItem.titleEn ?: details.baseItem.titlePl
        val year = details.metaInfo?.year
        val isTvShow = details.seasonsNumber != null

        updateState { it.copy(isLoadingMoreRecommendations = true) }

        launchHandled(
            onError = {
                updateState { it.copy(isLoadingMoreRecommendations = false) }
            },
        ) {
            val tmdbId = tmdbClient.getTmdbId(title, year, isTvShow) ?: return@launchHandled
            val nextPage = current.tmdbRecommendationsPage + 1
            val (tmdbMovies, totalPages) = tmdbClient.getRecommendations(tmdbId, isTvShow, nextPage)

            if (tmdbMovies.isNotEmpty()) {
                val newRecommendations = tmdbMovies.map { tmdbMovie ->
                    MovieItem(
                        url = "tmdb_${tmdbMovie.id}",
                        titlePl = tmdbMovie.title,
                        posterUrl = tmdbMovie.posterUrl,
                        year = tmdbMovie.releaseYear,
                        isTvShow = tmdbMovie.isTvShow,
                        detailsRequest = DetailsRequest.Search(
                            title = tmdbMovie.title,
                            year = tmdbMovie.releaseYear,
                            isTvShow = tmdbMovie.isTvShow,
                        ),
                    )
                }

                updateState {
                    it.copy(
                        tmdbRecommendations = it.tmdbRecommendations + newRecommendations,
                        tmdbRecommendationsPage = nextPage,
                        tmdbRecommendationsHasMore = nextPage < totalPages,
                        isLoadingMoreRecommendations = false,
                    )
                }
            } else {
                updateState {
                    it.copy(
                        tmdbRecommendationsHasMore = false,
                        isLoadingMoreRecommendations = false,
                    )
                }
            }
        }
    }

    override fun handleStaleData(staleData: Any) {
        val details = staleData as? DetailedMedia ?: return
        val isFavorite = favoritesManager?.isFavorite(details.baseItem.url) == true
        updateState {
            val nextState =
                it.copy(
                    mediaDetails = details,
                    isFavorite = isFavorite,
                )
            nextState.copy(
                selectedTabId = nextState.tabs.firstOrNull()?.id ?: TabRowItemId.Similar.id,
            )
        }
    }

    private fun mergeMediaDetails(current: DetailedMedia, new: DetailedMedia): DetailedMedia {
        val currentDesc = current.baseItem.description.ifEmpty { null }
        val newDesc = new.baseItem.description.ifEmpty { null }
        val bestDesc =
            if ((newDesc?.length ?: 0) > (currentDesc?.length ?: 0)) newDesc else currentDesc

        val bestPoster = current.baseItem.posterUrl.ifEmpty { new.baseItem.posterUrl }
        val bestBackdrop =
            current.baseItem.backgroundUrl.orEmpty().ifEmpty { new.baseItem.backgroundUrl }

        val bestImdbRating = current.baseItem.imdbRating ?: new.baseItem.imdbRating
        val bestFilmanRating = current.baseItem.filmanRating ?: new.baseItem.filmanRating
        val bestYear = current.baseItem.year ?: new.baseItem.year

        val mergedSeasons = if (current.baseItem.seasons != null || new.baseItem.seasons != null) {
            val allSeasons = (current.baseItem.seasons.orEmpty() + new.baseItem.seasons.orEmpty())
            val groupedBySeasonNumber = allSeasons.groupBy { season ->
                Regex("\\d+").find(season.name)?.value?.toIntOrNull() ?: 0
            }
            groupedBySeasonNumber.map { (seasonNumber, seasonsList) ->
                val seasonName =
                    seasonsList.firstOrNull { it.name.contains(seasonNumber.toString()) }?.name
                        ?: "Sezon $seasonNumber"
                val allEpisodes = seasonsList.flatMap { it.episodes }
                val mergedEpisodes = allEpisodes.distinctBy { ep ->
                    Regex("odcinek-(\\d+)|episode\\[(\\d+)\\]|/odcinek/(\\d+)").find(ep.url)?.value
                        ?: ep.title
                }.sortedBy { ep ->
                    Regex("odcinek-(\\d+)|episode\\[(\\d+)\\]|/odcinek/(\\d+)").find(ep.url)?.groupValues?.drop(
                        1,
                    )?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 0
                }
                com.pointlessapps.filman.data.model.Season(seasonName, mergedEpisodes)
            }.sortedBy { season ->
                Regex("\\d+").find(season.name)?.value?.toIntOrNull() ?: 0
            }.ifEmpty { null }
        } else {
            null
        }

        val mergedActors = (current.actors + new.actors).distinctBy { it.name }
        val mergedCategories = (current.categories + new.categories).distinctBy { it.name }
        val mergedTags = (current.tags + new.tags).distinctBy { it.name }
        val mergedSimilar = (current.similarMovies + new.similarMovies).distinctBy { it.titlePl }
        val mergedEmbeds = (current.embeds + new.embeds).distinctBy { it.url }

        val mergedMetaInfo = if (current.metaInfo != null || new.metaInfo != null) {
            val c = current.metaInfo
            val n = new.metaInfo
            com.pointlessapps.filman.data.model.MediaMetadata(
                year = c?.year ?: n?.year,
                views = c?.views ?: n?.views,
                duration = c?.duration ?: n?.duration,
                countries = (c?.countries.orEmpty() + n?.countries.orEmpty()).distinct(),
            )
        } else {
            null
        }

        val newBaseItem = current.baseItem.copy(
            description = bestDesc.orEmpty(),
            posterUrl = bestPoster,
            backgroundUrl = bestBackdrop,
            seasons = mergedSeasons,
            imdbRating = bestImdbRating,
            filmanRating = bestFilmanRating,
            year = bestYear,
        )

        return current.copy(
            baseItem = newBaseItem,
            actors = mergedActors,
            categories = mergedCategories,
            tags = mergedTags,
            similarMovies = mergedSimilar,
            embeds = mergedEmbeds,
            metaInfo = mergedMetaInfo,
        )
    }
}
