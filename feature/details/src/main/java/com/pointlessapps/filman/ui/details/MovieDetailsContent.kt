package com.pointlessapps.filman.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.ContextMenuOption
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.MediaCard
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.TabRowSectionItem
import com.pointlessapps.filman.ui.components.sections.episodesRowSection
import com.pointlessapps.filman.ui.components.sections.errorSection
import com.pointlessapps.filman.ui.components.sections.movieDetailsSection
import com.pointlessapps.filman.ui.components.sections.moviesGridSection
import com.pointlessapps.filman.ui.components.sections.posterSection
import com.pointlessapps.filman.ui.components.sections.tabRowSection
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.CREW
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.EPISODES
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FEATURED
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun MovieDetailsContent(
    state: MovieDetailsState,
    listState: LazyGridState,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    onMovieClicked: (sectionPrefix: String, movie: MovieItem) -> Unit,
    onWatchClicked: (sectionPrefix: String, url: String) -> Unit,
    onWatchTrailerClicked: (String) -> Unit,
    onPlayItem: (sectionPrefix: String, url: String) -> Unit,
    onActorClicked: (sectionPrefix: String, url: String) -> Unit,
    onTabSelected: (TabRowSectionItem) -> Unit,
    onOpenContextMenu: (MovieItem, Set<ContextMenuOption>) -> Unit,
    focusRestorationState: FocusRestorationState,
    onRefresh: () -> Unit,
    onLoadMoreRecommendations: () -> Unit,
) {
    val resources = LocalResources.current
    val progressMapState = rememberUpdatedState(state.shared.progressMap)

    CompositionLocalProvider(LocalFocusRestorationState provides focusRestorationState) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = listState,
            contentPadding =
                PaddingValues(horizontal = MaterialTheme.spacing.extraLarge)
                    .plus(PaddingValues(bottom = MaterialTheme.spacing.extraLarge)),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(contentFocusRequester),
        ) {
            errorSection(
                errorMessage = state.errorMessage,
                paddingValues = PaddingValues(),
                onRefresh = onRefresh,
            )

            if (state.errorMessage != null) return@LazyVerticalGrid

            val watchButtonText =
                when (val btnState = state.watchButtonState) {
                    is WatchButtonState.Default -> {
                        resources.getString(R.string.details_watch_now)
                    }

                    is WatchButtonState.Unavailable -> {
                        resources.getString(R.string.details_unavailable)
                    }

                    is WatchButtonState.WatchAgain -> {
                        resources.getString(R.string.details_watch_again)
                    }

                    is WatchButtonState.Continue -> {
                        resources.getString(R.string.details_continue)
                    }

                    is WatchButtonState.WatchNextEpisode -> {
                        resources.getString(
                            R.string.details_watch_next_episode,
                            btnState.season,
                            btnState.episode,
                        )
                    }

                    is WatchButtonState.ContinueEpisode -> {
                        resources.getString(
                            R.string.details_continue_episode,
                            btnState.season,
                            btnState.episode,
                        )
                    }
                }

            posterSection(
                detailedMedia = state.mediaDetails,
                watchButtonText = watchButtonText,
                isWatchButtonEnabled = state.watchButtonState !is WatchButtonState.Unavailable,
                trailerUrl = state.trailerUrl,
                onWatchClicked = {
                    val prefix = "${FEATURED.prefix}watch_button"
                    onWatchClicked(prefix, state.watchButtonState.url)
                },
                onWatchTrailerClicked = onWatchTrailerClicked,
                onMoreOptionsClicked = {
                    state.mediaDetails?.baseItem?.let { item ->
                        val isWatched = (state.shared.progressMap[item.url]
                            ?: 0f) >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
                        val watchOption = if (isWatched) {
                            ContextMenuOption.MARK_AS_NOT_WATCHED
                        } else {
                            ContextMenuOption.MARK_AS_WATCHED
                        }
                        onOpenContextMenu(
                            item,
                            setOf(
                                watchOption,
                                ContextMenuOption.FAVORITES,
                                ContextMenuOption.WATCHLIST,
                            ),
                        )
                    }
                },
                paddingValues = paddingValues,
            )

            if (state.tabs.isNotEmpty()) {
                tabRowSection(
                    items = state.tabs,
                    selectedTabId = state.selectedTabId,
                    onTabSelected = onTabSelected,
                )

                when (state.selectedTabId) {
                    TabRowItemId.Episodes.id -> {
                        val seasons =
                            state.mediaDetails
                                ?.baseItem
                                ?.seasons
                                .orEmpty()
                        seasons.forEachIndexed { index, season ->
                            episodesRowSection(
                                title = resources.getString(
                                    R.string.details_season_number,
                                    index + 1,
                                ),
                                items = state.getSeasonEpisodes(season, index),
                                onItemClicked = {
                                    val prefix = "${EPISODES.prefix}${
                                        resources.getString(
                                            R.string.details_season_number,
                                            index + 1,
                                        )
                                    }"
                                    onPlayItem(prefix, it.url)
                                },
                                onItemLongClicked = { item ->
                                    val isWatched =
                                        (
                                                state.shared.progressMap[item.url]
                                                    ?: 0f
                                                ) >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
                                    val watchOptions =
                                        if (isWatched) {
                                            setOf(ContextMenuOption.MARK_AS_NOT_WATCHED)
                                        } else {
                                            setOf(
                                                ContextMenuOption.MARK_AS_WATCHED,
                                                ContextMenuOption.MARK_PREVIOUS_AS_WATCHED,
                                            )
                                        }
                                    onOpenContextMenu(
                                        MovieItem(
                                            url = item.url,
                                            titlePl = item.titlePl,
                                            posterUrl =
                                                state.mediaDetails
                                                    ?.baseItem
                                                    ?.posterUrl
                                                    .orEmpty(),
                                            seriesUrl =
                                                state.mediaDetails
                                                    ?.baseItem
                                                    ?.url
                                                    .orEmpty(),
                                            seasonNumber = item.season,
                                            episodeNumber = item.episode,
                                            nextEpisodeUrl = item.nextEpisodeUrl,
                                        ),
                                        watchOptions,
                                    )
                                },
                            )
                        }
                    }

                    TabRowItemId.Details.id -> {
                        movieDetailsSection(
                            detailedMedia = state.mediaDetails,
                            onActorClicked = { title, actor ->
                                val prefix = "${CREW.prefix}$title"
                                onActorClicked(prefix, actor.url)
                            },
                        )
                    }

                    TabRowItemId.Similar.id -> {
                        moviesGridSection(
                            title = null,
                            items =
                                state.mediaDetails
                                    ?.similarMovies
                                    .orEmpty()
                                    .map(MoviesGridItem::Single),
                            isLoadingNextPage = false,
                            onItemClicked = {
                                onMovieClicked(RECOMMENDED.prefix, it.movieItem)
                            },
                            onItemLongClicked = { item ->
                                val isWatched =
                                    (
                                            state.shared.progressMap[item.movieItem.url]
                                                ?: 0f
                                            ) >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
                                val watchOption =
                                    if (isWatched) {
                                        ContextMenuOption.MARK_AS_NOT_WATCHED
                                    } else {
                                        ContextMenuOption.MARK_AS_WATCHED
                                    }
                                onOpenContextMenu(
                                    item.movieItem,
                                    setOf(
                                        watchOption,
                                        ContextMenuOption.FAVORITES,
                                        ContextMenuOption.WATCHLIST,
                                    ),
                                )
                            },
                            onLoadNextPageRequest = { },
                            showLoadMoreButton = false,
                            onShowMoreClicked = { },
                            progressProvider = { progressMapState.value },
                        )
                    }

                    TabRowItemId.Recommended.id -> {
                        moviesGridSection(
                            title = null,
                            items = state.tmdbRecommendations.map(MoviesGridItem::Single),
                            isLoadingNextPage = state.isLoadingMoreRecommendations,
                            onItemClicked = {
                                onMovieClicked(RECOMMENDED.prefix, it.movieItem)
                            },
                            onItemLongClicked = { item ->
                                val isWatched =
                                    (
                                            state.shared.progressMap[item.movieItem.url]
                                                ?: 0f
                                            ) >= MARK_AS_WATCHED_PROGRESS_THRESHOLD
                                val watchOption =
                                    if (isWatched) {
                                        ContextMenuOption.MARK_AS_NOT_WATCHED
                                    } else {
                                        ContextMenuOption.MARK_AS_WATCHED
                                    }
                                onOpenContextMenu(
                                    item.movieItem,
                                    setOf(
                                        watchOption,
                                        ContextMenuOption.FAVORITES,
                                        ContextMenuOption.WATCHLIST,
                                    ),
                                )
                            },
                            onLoadNextPageRequest = {
                                if (state.tmdbRecommendationsHasMore) {
                                    onLoadMoreRecommendations()
                                }
                            },
                            showLoadMoreButton = false,
                            onShowMoreClicked = { },
                            progressProvider = { progressMapState.value },
                        )
                    }
                }
            }
        }
    }
}
