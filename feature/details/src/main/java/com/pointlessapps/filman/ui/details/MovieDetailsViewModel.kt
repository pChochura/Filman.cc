package com.pointlessapps.filman.ui.details

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.TiwiRepository
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.YoutubeSearchService
import com.pointlessapps.filman.data.api.pollStreamLinks
import com.pointlessapps.filman.data.api.toEmbedLinks
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.WatchlistManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.TmdbFeed
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.buildStandardOverlayContextMenu
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.details.MovieDetailsEffect.NavigateToActor
import com.pointlessapps.filman.ui.details.MovieDetailsEffect.NavigateToPlayer

class MovieDetailsViewModel(
    private val repository: TiwiRepository,
    private val videoUrlResolver: VideoUrlResolver,
    private val youtubeSearchService: YoutubeSearchService,
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
                    state.copy(progressList = progressList)
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
        when (event) {
            is BaseEvent.OpenContextMenu -> {
                val menuData = buildStandardOverlayContextMenu(
                    movie = event.movie,
                    progressMap = currentState.shared.progressMap,
                    favoritesManager = favoritesManager,
                    watchlistManager = watchlistManager,
                    options = event.options,
                    onEvent = ::onEvent,
                    isSearchingYoutube = currentState.isSearchingYoutube,
                )
                updateSharedState { it.copy(overlayMenuData = menuData) }
            }

            is BaseEvent.WatchOnYoutube -> {
                val details = currentState.mediaDetails ?: return
                if (currentState.isSearchingYoutube) return

                updateState {
                    it.copy(
                        isSearchingYoutube = true,
                        shared = it.shared.copy(
                            overlayMenuData = it.shared.overlayMenuData?.let { menu ->
                                menu.copy(
                                    items = menu.items.map { item ->
                                        if (item is TiwiOverlayMenuItem.Button &&
                                            item.label == TextValue.StringResource(R.string.common_watch_on_youtube)
                                        ) {
                                            item.copy(isLoading = true)
                                        } else {
                                            item
                                        }
                                    },
                                )
                            },
                        ),
                    )
                }

                launchHandled(
                    onError = {
                        updateState {
                            it.copy(
                                isSearchingYoutube = false,
                                shared = it.shared.copy(
                                    overlayMenuData = it.shared.overlayMenuData?.let { menu ->
                                        menu.copy(
                                            items = menu.items.map { item ->
                                                if (item is TiwiOverlayMenuItem.Button &&
                                                    item.label == TextValue.StringResource(R.string.common_watch_on_youtube)
                                                ) {
                                                    item.copy(isLoading = false)
                                                } else {
                                                    item
                                                }
                                            },
                                        )
                                    },
                                ),
                            )
                        }
                        sendEffect(
                            MovieDetailsEffect.ShowToast(
                                TextValue.StringResource(R.string.common_youtube_not_found),
                            ),
                        )
                    },
                ) {
                    val youtubeUrl = youtubeSearchService.searchFullMovie(details)
                    if (youtubeUrl != null) {
                        updateState {
                            it.copy(
                                isSearchingYoutube = false,
                                shared = it.shared.copy(overlayMenuData = null),
                            )
                        }
                        val title = details.baseItem.titlePl.ifBlank { null } ?: details.baseItem.titleEn
                        val description = details.baseItem.description
                        sendEffect(
                            NavigateToPlayer(
                                url = youtubeUrl,
                                title = title,
                                description = description,
                                isTrailer = false,
                            ),
                        )
                    } else {
                        updateState {
                            it.copy(
                                isSearchingYoutube = false,
                                shared = it.shared.copy(
                                    overlayMenuData = it.shared.overlayMenuData?.let { menu ->
                                        menu.copy(
                                            items = menu.items.map { item ->
                                                if (item is TiwiOverlayMenuItem.Button &&
                                                    item.label == TextValue.StringResource(R.string.common_watch_on_youtube)
                                                ) {
                                                    item.copy(isLoading = false)
                                                } else {
                                                    item
                                                }
                                            },
                                        )
                                    },
                                ),
                            )
                        }
                        sendEffect(
                            MovieDetailsEffect.ShowToast(
                                TextValue.StringResource(R.string.common_youtube_not_found),
                            ),
                        )
                    }
                }
            }

            is BaseEvent.MarkPreviousAsWatched -> {
                val details = currentState.mediaDetails ?: return
                val currentSeason = event.movie.seasonNumber ?: return
                val currentEpisode = event.movie.episodeNumber ?: return

                val itemsToSave = details.collectEpisodesUpTo(currentSeason, currentEpisode)
                progressManager?.markAsWatched(itemsToSave)
            }

            else -> super.handleBaseEvent(event)
        }
    }

    override fun handleEvent(event: MovieDetailsEvent) {
        when (event) {
            is MovieDetailsEvent.OpenActorDetails -> sendEffect(NavigateToActor(event.url))
            is MovieDetailsEvent.LoadDetails -> loadDetails(event.request)
            is MovieDetailsEvent.PlayItem -> {
                val details = currentState.mediaDetails?.baseItem
                val title = details?.titlePl?.ifBlank { null } ?: details?.titleEn
                val description = details?.description
                sendEffect(
                    NavigateToPlayer(
                        url = event.url,
                        title = title,
                        description = description,
                        isTrailer = false,
                    ),
                )
            }
            is MovieDetailsEvent.WatchTrailer -> {
                val details = currentState.mediaDetails?.baseItem
                val title = details?.titlePl?.ifBlank { null } ?: details?.titleEn
                val description = details?.description
                sendEffect(
                    NavigateToPlayer(
                        url = event.url,
                        title = title,
                        description = description,
                        isTrailer = true,
                    ),
                )
            }
            is MovieDetailsEvent.TabChanged -> updateState { it.copy(selectedTabId = event.tab.id) }
            is MovieDetailsEvent.LoadMoreRecommendations -> loadMoreRecommendations()
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
                shared = it.shared.copy(isLoading = true, errorMessage = null),
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
                        errorMessage = it.message?.let(TextValue::DynamicString)
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
                val nextState = it.copy(
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
                    selectedTabId = nextState.tabs.firstOrNull()?.id ?: TabRowItemId.Similar.id,
                )
            }

            val isTvShow = details.baseItem.isTvShow || details.seasonsNumber != null
            if (!isTvShow) {
                startMovieStreamPolling(canonicalKey)
            } else {
                startTvEpisodePrefetch(currentState)
            }
        }
    }

    private fun startTvEpisodePrefetch(state: MovieDetailsState) {
        val targetEpisodeUrl = when (val btn = state.watchButtonState) {
            is WatchButtonState.ContinueEpisode -> btn.url
            is WatchButtonState.WatchNextEpisode -> btn.url
            is WatchButtonState.Default -> btn.url
            else -> null
        }
        targetEpisodeUrl?.let { epUrl ->
            launchHandled(onError = {}) {
                videoUrlResolver.prefetch(epUrl)
            }
        }
    }

    private fun startMovieStreamPolling(canonicalKey: String) {
        launchHandled(
            onError = {
                updateState { state -> state.copy(isExtractingLinks = false) }
                handleError(it)
            },
        ) {
            updateState { it.copy(isExtractingLinks = true) }
            repository.pollStreamLinks(
                url = canonicalKey,
                isTvShow = false,
                maxAttempts = 220,
                delayMs = 250L,
            ) { linksResponse ->
                if (linksResponse.status == "ERROR" && linksResponse.links.isEmpty()) {
                    sendEffect(
                        MovieDetailsEffect.ShowToast(TextValue.StringResource(R.string.stream_api_unavailable)),
                    )
                }
                val mappedEmbeds = videoUrlResolver.sortEmbeds(linksResponse.links.toEmbedLinks())
                updateState {
                    it.copy(mediaDetails = it.mediaDetails?.copy(embeds = mappedEmbeds))
                }
                if (linksResponse.status == "PARTIAL" && mappedEmbeds.isNotEmpty()) {
                    updateState { it.copy(isExtractingLinks = false) }
                }
            }
            updateState { it.copy(isExtractingLinks = false) }
        }
    }

    private fun loadMoreRecommendations() {
        val current = currentState
        if (current.isLoadingMoreRecommendations || !current.tmdbRecommendationsHasMore) return
        val details = current.mediaDetails ?: return
        val ref = details.baseItem.mediaRef
            ?: MediaRef.parse(details.baseItem.url, details.baseItem.isTvShow)
            ?: return

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
