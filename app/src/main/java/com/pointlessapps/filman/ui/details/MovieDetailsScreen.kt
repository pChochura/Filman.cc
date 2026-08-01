package com.pointlessapps.filman.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.R
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.ContextMenuOption
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.sections.TabRowSectionItem
import com.pointlessapps.filman.ui.components.sections.episodesRowSection
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
internal fun MovieDetailsScreen(
    movieUrl: String,
    autoPlay: Boolean = false,
    episodeUrl: String? = null,
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: MovieDetailsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val returnFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyGridState()

    LaunchedEffect(movieUrl) {
        viewModel.onEvent(MovieDetailsEvent.LoadDetails(movieUrl))
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is MovieDetailsEffect.NavigateToAuth -> onNavigateTo(Route.Login())
            is MovieDetailsEffect.NavigateToPlayer -> onNavigateTo(Route.Player(effect.url))
            is MovieDetailsEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
            is MovieDetailsEffect.NavigateToActor -> onNavigateTo(Route.Actor(effect.url))
        }
    }

    var hasAutoPlayed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isLoading) {
        if (autoPlay && !hasAutoPlayed && !state.isLoading && state.mediaDetails != null) {
            hasAutoPlayed = true
            val url = episodeUrl ?: state.watchButtonState.url
            if (url.isNotEmpty()) {
                viewModel.onEvent(MovieDetailsEvent.PlayItem(url))
            }
        }
    }

    LifecycleResumeEffect(state.isLoading) {
        if (!state.isLoading) {
            coroutineScope.launch {
                delay(100.milliseconds)
                if (lastFocusedItemId != null) {
                    returnFocusRequester.requestFocus()
                } else {
                    contentFocusRequester.requestFocus()
                }
            }
        }

        onPauseOrDispose { }
    }

    val isPosterSectionVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < 50
        }
    }
    BackHandler(!isPosterSectionVisible) {
        coroutineScope.launch {
            if (listState.firstVisibleItemIndex > 0) {
                listState.scrollToItem(1)
            }
            listState.animateScrollToItem(0)
            contentFocusRequester.requestFocus()
        }
    }

    Crossfade(
        targetState = state.isLoading,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            MovieDetailsContent(
                state = state,
                listState = listState,
                contentFocusRequester = contentFocusRequester,
                paddingValues = paddingValues,
                onMovieClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url))
                },
                onPlayItem = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(MovieDetailsEvent.PlayItem(url))
                },
                onActorClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(MovieDetailsEvent.OpenActorDetails(url))
                },
                onToggleFavorite = { viewModel.onEvent(MovieDetailsEvent.ToggleFavorite) },
                onTabSelected = { viewModel.onEvent(MovieDetailsEvent.TabChanged(it)) },
                onOpenContextMenu = { movie, options ->
                    viewModel.onEvent(BaseEvent.OpenContextMenu(movie, options))
                },
                onWatchClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "${sectionPrefix}watch_button"
                    viewModel.onEvent(MovieDetailsEvent.PlayItem(url))
                },
                focusRestorationState = FocusRestorationState(
                    focusRequester = returnFocusRequester,
                    lastFocusedItemKey = lastFocusedItemId,
                ),
            )
        }
    }

    state.overlayMenuData?.let { data ->
        FilmanOverlayMenu(
            title = data.title,
            items = data.items,
            onDismissRequest = { viewModel.onEvent(BaseEvent.CloseContextMenu) },
        )
    }
}

@Composable
private fun MovieDetailsContent(
    state: MovieDetailsState,
    listState: LazyGridState,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    onMovieClicked: (sectionPrefix: String, url: String) -> Unit,
    onWatchClicked: (sectionPrefix: String, url: String) -> Unit,
    onPlayItem: (sectionPrefix: String, url: String) -> Unit,
    onActorClicked: (sectionPrefix: String, url: String) -> Unit,
    onToggleFavorite: () -> Unit,
    onTabSelected: (TabRowSectionItem) -> Unit,
    onOpenContextMenu: (MovieItem, Set<ContextMenuOption>) -> Unit,
    focusRestorationState: FocusRestorationState,
) {
    val resources = LocalResources.current

    CompositionLocalProvider(LocalFocusRestorationState provides focusRestorationState) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = listState,
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge)
                .plus(PaddingValues(bottom = MaterialTheme.spacing.extraLarge)),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester),
        ) {
            val watchButtonText = when (val btnState = state.watchButtonState) {
                is WatchButtonState.Default -> resources.getString(R.string.details_watch_now)
                is WatchButtonState.WatchAgain -> resources.getString(R.string.details_watch_again)
                is WatchButtonState.Continue -> resources.getString(R.string.details_continue)
                is WatchButtonState.WatchNextEpisode -> resources.getString(
                    R.string.details_watch_next_episode,
                    btnState.season,
                    btnState.episode,
                )

                is WatchButtonState.ContinueEpisode -> resources.getString(
                    R.string.details_continue_episode,
                    btnState.season,
                    btnState.episode,
                )
            }

            posterSection(
                detailedMedia = state.mediaDetails,
                isFavourite = state.isFavorite,
                watchButtonText = watchButtonText,
                onWatchClicked = {
                    val prefix = "${FEATURED.prefix}watch_button"
                    onWatchClicked(prefix, state.watchButtonState.url)
                },
                onToggleFavouritesClicked = onToggleFavorite,
                paddingValues = paddingValues,
            )

            tabRowSection(
                items = state.tabs,
                selectedTabId = state.selectedTabId,
                onTabSelected = onTabSelected,
            )

            when (state.selectedTabId) {
                TabRowItemId.Episodes.id -> {
                    val seasons = state.mediaDetails?.baseItem?.seasons.orEmpty()
                    seasons.forEachIndexed { index, season ->
                        episodesRowSection(
                            title = resources.getString(R.string.details_season_number, index + 1),
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
                                val isWatched = state.progressMap[item.url] is ProgressItem.Watched
                                val watchOptions = if (isWatched) {
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
                                        posterUrl = state.mediaDetails?.baseItem?.posterUrl.orEmpty(),
                                        seriesUrl = state.mediaDetails?.baseItem?.url.orEmpty(),
                                        seasonNumber = item.season,
                                        episodeNumber = item.episode,
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
                        items = state.mediaDetails?.similarMovies.orEmpty(),
                        isLoadingNextPage = false,
                        onItemClicked = { onMovieClicked(RECOMMENDED.prefix, it.url) },
                        onItemLongClicked = { item ->
                            val isWatched = state.progressMap[item.url] is ProgressItem.Watched
                            val watchOption = if (isWatched) {
                                ContextMenuOption.MARK_AS_NOT_WATCHED
                            } else {
                                ContextMenuOption.MARK_AS_WATCHED
                            }
                            onOpenContextMenu(item, setOf(watchOption, ContextMenuOption.FAVORITES))
                        },
                        onLoadNextPageRequest = { },
                        showLoadMoreButton = false,
                        onShowMoreClicked = { },
                    )
                }
            }
        }
    }
}
