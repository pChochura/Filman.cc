package com.pointlessapps.filman.ui.home

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.R
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.data.local.FavoritesManager
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.recommendation.RecommendationManager
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseEvent.RemoveFromContinueWatching
import com.pointlessapps.filman.ui.base.BaseEvent.RemoveFromFavorites
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.MoviesSection
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine

internal sealed interface HomeEvent : FilmanEvent {
    data object LoadHomeData : HomeEvent
}

@Immutable
internal data class HomeState(
    override val shared: SharedState = SharedState(),
    val progressItems: List<ProgressItem> = emptyList(),
    val favorites: List<MovieItem> = emptyList(),
) : StateWithShared<HomeState> {
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface HomeEffect {
    data object ScrollToTop : HomeEffect

    data object FocusFirstGridItem : HomeEffect

    data object NavigateToAuth : HomeEffect

    data class NavigateToDetails(
        val request: DetailsRequest,
        val autoplay: Boolean,
        val episodeUrl: String? = null,
    ) : HomeEffect

    data class OverrideFocus(
        val itemId: String,
    ) : HomeEffect
}

internal class HomeViewModel(
    private val scraper: FilmanScraper,
    private val recommendationManager: RecommendationManager,
    favoritesManager: FavoritesManager,
    progressManager: ProgressManager,
) : BaseViewModel<HomeState, HomeEvent, HomeEffect>(
    initialState = HomeState(),
    favoritesManager = favoritesManager,
    progressManager = progressManager,
) {
    private var currentLoadJob: Job? = null

    init {
        launchHandled {
            combine(
                favoritesManager.favoritesFlow,
                progressManager.progressItemsFlow,
            ) { favorites, progressItems ->
                val distinctSeries =
                    progressItems.distinctBy { p ->
                        p.parentUrl?.substringAfter(FilmanConfig.DOMAIN)?.trimEnd('/')
                    }

                val fullyWatchedUrls =
                    distinctSeries.mapNotNull { p ->
                        if (p is ProgressItem.Watched) {
                            if (p.parentUrl == null || p.parentUrl == p.url) {
                                p.url.substringAfter(FilmanConfig.DOMAIN).trimEnd('/')
                            } else if (!p.hasNextEpisode) {
                                p.parentUrl.substringAfter(FilmanConfig.DOMAIN).trimEnd('/')
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }.toSet()

                val filteredFavorites =
                    favorites.filterNot {
                        it.url.substringAfter(FilmanConfig.DOMAIN).trimEnd('/') in fullyWatchedUrls
                    }

                val mappedProgressItems =
                    distinctSeries.mapNotNull { p ->
                        if (p is ProgressItem.Watched) {
                            if (p.parentUrl != null && p.parentUrl != p.url && p.hasNextEpisode) {
                                ProgressItem.NextEpisode(
                                    url = p.url,
                                    parentUrl = p.parentUrl,
                                    posterUrl = p.posterUrl,
                                    titlePl = p.seriesTitle ?: p.titlePl,
                                    seriesTitle = p.seriesTitle,
                                )
                            } else {
                                null
                            }
                        } else {
                            p
                        }
                    }

                filteredFavorites to mappedProgressItems
            }.collect { (filteredFavorites, mappedProgressItems) ->
                updateState {
                    it.copy(
                        favorites = filteredFavorites,
                        progressItems = mappedProgressItems,
                    )
                }
            }
        }
    }

    override fun getAuthErrorEffect(): HomeEffect = HomeEffect.NavigateToAuth

    override fun getNavigateToDetailsEffect(
        url: String,
        autoplay: Boolean,
        episodeUrl: String?,
        request: DetailsRequest?,
    ): HomeEffect = HomeEffect.NavigateToDetails(
        request = request ?: DetailsRequest.Url(url),
        autoplay = autoplay,
        episodeUrl = episodeUrl,
    )

    override fun handleEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.LoadHomeData -> loadData()
        }
    }

    override fun handleBaseEvent(event: BaseEvent) {
        when (event) {
            is RemoveFromFavorites -> {
                val isLastItem =
                    currentState.favorites.size == 1 &&
                            currentState.favorites.first().url == event.url
                super.handleBaseEvent(event)
                if (isLastItem) {
                    val fallbackId =
                        currentState.progressItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.CONTINUE_WATCHING.prefix}$it"
                        } ?: currentState.featuredItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.FEATURED.prefix}$it"
                        }
                    if (fallbackId != null) {
                        sendEffect(HomeEffect.OverrideFocus(fallbackId))
                    }
                }
            }

            is RemoveFromContinueWatching -> {
                val isLastItem =
                    currentState.progressItems.size == 1 &&
                            currentState.progressItems.first().url == event.url
                super.handleBaseEvent(event)
                if (isLastItem) {
                    val fallbackId =
                        currentState.featuredItems.lastOrNull()?.url?.let {
                            "${SectionFocusRestorationId.FEATURED.prefix}$it"
                        }
                    if (fallbackId != null) {
                        sendEffect(HomeEffect.OverrideFocus(fallbackId))
                    }
                }
            }

            else -> {
                super.handleBaseEvent(event)
            }
        }
    }

    override fun handleStaleData(staleData: Any) {
        val result = staleData as? PageResult ?: return
        updateSharedState {
            it.copy(
                featuredItems = result.featuredItems,
                moviesSections =
                    listOf(
                        MoviesSection(
                            title = R.string.home_recommended,
                            movies = result.movies.map(MoviesGridItem::Single),
                            path = result.path,
                            page = 1,
                            hasMore = result.movies.size >= 20,
                        ),
                    ),
            )
        }
    }

    private fun loadData() {
        if (currentState.moviesSections.isNotEmpty()) return

        updateSharedState {
            it.copy(
                isLoading = true,
                errorMessage = null,
            )
        }

        currentLoadJob?.cancel()
        var thisJob: Job? = null
        thisJob =
            launchHandled(
                onError = { t ->
                    if (thisJob?.isCancelled == true) return@launchHandled
                    updateSharedState {
                        it.copy(
                            isLoading = false,
                            errorMessage =
                                t.message?.let(TextValue::DynamicString)
                                    ?: TextValue.StringResource(R.string.error_unknown),
                        )
                    }
                    handleError(t)
                },
            ) {
                val resultDeferred = async { scraper.getCategoryPage(FilmanConfig.PATH_HOME) }
                val personalizedDeferred =
                    async { recommendationManager.getPersonalizedRecommendations() }

                val result = resultDeferred.await()
                val personalized = personalizedDeferred.await()

                if (result.errorMessage != null) {
                    updateSharedState {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.errorMessage.let(TextValue::DynamicString),
                        )
                    }
                } else {
                    val sections = mutableListOf<MoviesSection>()

                    if (personalized.isNotEmpty()) {
                        sections.add(
                            MoviesSection(
                                title = R.string.home_for_you,
                                movies = personalized.map(MoviesGridItem::Single),
                            ),
                        )
                    }

                    sections.add(
                        MoviesSection(
                            title = R.string.home_recommended,
                            movies = result.movies.map(MoviesGridItem::Single),
                        ),
                    )

                    updateSharedState {
                        it.copy(
                            featuredItems = result.featuredItems,
                            moviesSections = sections,
                            isLoading = false,
                        )
                    }
                    sendEffect(HomeEffect.ScrollToTop)
                }
            }
        currentLoadJob = thisJob
    }
}
