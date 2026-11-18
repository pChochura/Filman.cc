package com.pointlessapps.filman.ui.details

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.TabRowSectionItem
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.details.MovieDetailsEffect.NavigateToActor
import com.pointlessapps.filman.ui.details.MovieDetailsEffect.NavigateToPlayer
import kotlinx.coroutines.delay

sealed interface MovieDetailsEvent : FilmanEvent {
    data class OpenActorDetails(
        val url: String,
    ) : MovieDetailsEvent

    data class LoadDetails(
        val request: DetailsRequest,
    ) : MovieDetailsEvent

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
data class MovieDetailsState(
    override val shared: SharedState = SharedState(),
    val mediaDetails: DetailedMedia? = null,
    val isExtractingLinks: Boolean = false,
    val isFavorite: Boolean = false,
    val isInWatchlist: Boolean = false,
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

enum class TabRowItemId(
    val id: Int,
) {
    Episodes(0),
    Recommended(1),
    Similar(2),
    Details(3),
}

sealed interface WatchButtonState {
    val url: String

    data class Default(
        override val url: String = "",
    ) : WatchButtonState

    data object Unavailable : WatchButtonState {
        override val url: String = ""
    }

    data object Loading : WatchButtonState {
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

sealed interface MovieDetailsEffect {
    data class ShowToast(val message: TextValue) : MovieDetailsEffect

    data object NavigateToAuth : MovieDetailsEffect

    data object NavigateBack : MovieDetailsEffect

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

class MovieDetailsViewModel(
    private val repository: FilmanRepository,
    private val videoUrlResolver: VideoUrlResolver,
    favoritesManager: FavoritesManager,
    watchlistManager: WatchlistManager,
    progressManager: ProgressManager,
) : BaseViewModel<MovieDetailsState, MovieDetailsEvent, MovieDetailsEffect>(
    initialState = MovieDetailsState(),
    favoritesManager = favoritesManager,
    watchlistManager = watchlistManager,
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
                    val firstEpRef = season.episodes.firstOrNull()?.let { MediaRef.parse(it.url, isTvShowHint = true) }
                    val seasonNum = firstEpRef?.season ?: (sIndex + 1)
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

                        val epRef = MediaRef.parse(ep.url, isTvShowHint = true)
                        val epNumber = epRef?.episode ?: (eIndex + 1)

                        val epMovie =
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

            is MovieDetailsEvent.PlayItem -> {
                sendEffect(NavigateToPlayer(event.url))
            }

            is MovieDetailsEvent.WatchTrailer -> {
                sendEffect(NavigateToPlayer(event.url))
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
        val ref = request.mediaRef
        val url = when (request) {
            is DetailsRequest.Url -> request.url
            is DetailsRequest.ByRef -> request.ref.canonicalKey
        }
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
                tmdbRecommendations = emptyList(),
                tmdbRecommendationsPage = 1,
                tmdbRecommendationsHasMore = false,
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
            val details = repository.getMediaDetails(url, ref?.isTvShow)
            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                    )
                }
                return@launchHandled
            }

            val canonicalKey = details.baseItem.url
            val isFavorite = favoritesManager?.isFavorite(canonicalKey) == true
            val isWatchlist = watchlistManager?.isInWatchlist(canonicalKey) == true

            updateState {
                val nextState =
                    it.copy(
                        shared = it.shared.copy(isLoading = false),
                        mediaDetails = details,
                        isFavorite = isFavorite,
                        isInWatchlist = isWatchlist,
                        tmdbRecommendations = details.recommendations,
                        tmdbRecommendationsPage = 1,
                        tmdbRecommendationsHasMore = details.recommendationsTotalPages > 1,
                        trailerUrl = details.trailerUrl,
                    )
                nextState.copy(
                    selectedTabId = nextState.tabs.firstOrNull()?.id
                        ?: TabRowItemId.Similar.id,
                )
            }

            val isTvShow = details.baseItem.isTvShow || details.seasonsNumber != null
            if (!isTvShow) {
                launchHandled(onError = { updateState { state -> state.copy(isExtractingLinks = false) }; handleError(it) }) {
                    updateState { it.copy(isExtractingLinks = true) }
                    var isFinished = false
                    var attempts = 0
                    while (!isFinished && attempts < 220) {
                        attempts++
                        val linksResponse = repository.getLinks(canonicalKey, isTvShow = false)
                        if (linksResponse != null) {
                            if (linksResponse.status == "ERROR" && linksResponse.links.isEmpty()) {
                                sendEffect(MovieDetailsEffect.ShowToast(TextValue.StringResource(R.string.stream_api_unavailable)))
                            }
                            val mappedEmbeds = videoUrlResolver.sortEmbeds(
                                linksResponse.links.map {
                                    EmbedLink(
                                        url = it.url,
                                        serverName = it.serverName.ifEmpty { "${it.sourceWebsite} ${it.language}".trim() },
                                        version = it.language,
                                        quality = it.quality,
                                        sourceWebsite = it.sourceWebsite,
                                        headers = it.headers,
                                        subtitles = it.subtitles.map { sub ->
                                            Subtitle(
                                                url = sub.url,
                                                label = sub.label,
                                                language = sub.language,
                                            )
                                        },
                                    )
                                },
                            )
                            updateState {
                                it.copy(
                                    mediaDetails = it.mediaDetails?.copy(embeds = mappedEmbeds),
                                )
                            }
                            if (linksResponse.status == "READY" || linksResponse.status == "ERROR") {
                                isFinished = true
                            } else if (linksResponse.status == "PARTIAL" && mappedEmbeds.isNotEmpty()) {
                                updateState { it.copy(isExtractingLinks = false) }
                            }
                        } else {
                            isFinished = true
                        }
                        if (!isFinished) {
                            delay(250)
                        }
                    }
                    updateState { it.copy(isExtractingLinks = false) }
                }
            }
        }
    }

    private fun loadMoreRecommendations() {
        val current = currentState
        if (current.isLoadingMoreRecommendations || !current.tmdbRecommendationsHasMore) return
        val details = current.mediaDetails ?: return
        val ref = details.baseItem.mediaRef ?: MediaRef.parse(details.baseItem.url, details.baseItem.isTvShow) ?: return

        updateState { it.copy(isLoadingMoreRecommendations = true) }

        launchHandled(
            onError = {
                updateState { it.copy(isLoadingMoreRecommendations = false) }
            },
        ) {
            val nextPage = current.tmdbRecommendationsPage + 1
            val pageResult = repository.getFeedPage(TmdbFeed.Recommendations(ref.parentRef), nextPage)

            if (pageResult.movies.isNotEmpty()) {
                updateState {
                    it.copy(
                        tmdbRecommendations = (it.tmdbRecommendations + pageResult.movies).distinctBy { item -> item.url },
                        tmdbRecommendationsPage = nextPage,
                        tmdbRecommendationsHasMore = pageResult.hasMore,
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
}
