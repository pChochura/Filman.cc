package com.example.filman.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.example.filman.R
import com.example.filman.Route
import com.example.filman.ui.base.BaseEvent
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.components.sections.episodesRowSection
import com.example.filman.ui.components.sections.movieDetailsSection
import com.example.filman.ui.components.sections.moviesGridSection
import com.example.filman.ui.components.sections.posterSection
import com.example.filman.ui.components.sections.tabRowSection
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.FocusRestorationState
import com.example.filman.ui.core.LocalFocusRestorationState
import com.example.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun MovieDetailsScreen(
    movieUrl: String,
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
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
            is MovieDetailsEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
            is MovieDetailsEffect.NavigateToPlayer -> onNavigateTo(Route.Player(effect.url))
            is MovieDetailsEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
            is MovieDetailsEffect.NavigateToActor -> onNavigateTo(Route.Actor(effect.url))
        }
    }

    LifecycleResumeEffect(Unit) {
        coroutineScope.launch {
            delay(100.milliseconds)
            runCatching {
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

    AnimatedContent(
        targetState = state.isLoading,
        contentAlignment = Alignment.Center,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            MovieDetailsContent(
                state = state,
                listState = listState,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                onItemClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url))
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
    onEvent: (FilmanEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    onItemClicked: (sectionPrefix: String, url: String) -> Unit,
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
                sectionFocusRequester = contentFocusRequester,
                onWatchClicked = {
                    val url = state.watchButtonState.url
                    if (url.isNotEmpty()) {
                        onEvent(MovieDetailsEvent.PlayItem(url))
                    }
                },
                onToggleFavouritesClicked = { onEvent(MovieDetailsEvent.ToggleFavorite) },
            )

            tabRowSection(
                items = state.tabs,
                selectedTabId = state.selectedTabId,
                onTabSelected = { onEvent(MovieDetailsEvent.TabChanged(it)) },
            )

            when (state.selectedTabId) {
                TabRowItemId.Episodes.id -> {
                    val seasons = state.mediaDetails?.baseItem?.seasons.orEmpty()
                    seasons.forEachIndexed { index, season ->
                        episodesRowSection(
                            title = resources.getString(R.string.details_season_number, index + 1),
                            items = state.getSeasonEpisodes(season),
                            onItemClicked = { onEvent(MovieDetailsEvent.PlayItem(it.url)) },
                            onItemLongClicked = { item ->
                                onEvent(
                                    BaseEvent.OpenContextMenu(
                                        title = item.titlePl,
                                        url = item.url,
                                        posterUrl = item.posterUrl,
                                    ),
                                )
                            },
                        )
                    }
                }

                TabRowItemId.Details.id -> {
                    movieDetailsSection(
                        detailedMedia = state.mediaDetails,
                        onActorClicked = {
                            onEvent(MovieDetailsEvent.OpenActorDetails(it.url))
                        },
                    )
                }

                TabRowItemId.Similar.id -> {
                    moviesGridSection(
                        title = null,
                        items = state.mediaDetails?.similarMovies.orEmpty(),
                        isLoadingNextPage = false,
                        onItemClicked = { onItemClicked(RECOMMENDED.prefix, it.url) },
                        onItemLongClicked = { item ->
                            onEvent(
                                BaseEvent.OpenContextMenu(
                                    title = item.titlePl,
                                    url = item.url,
                                    posterUrl = item.posterUrl,
                                ),
                            )
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
