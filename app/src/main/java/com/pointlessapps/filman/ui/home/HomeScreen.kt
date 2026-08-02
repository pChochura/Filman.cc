package com.pointlessapps.filman.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.sections.continueWatchingSection
import com.pointlessapps.filman.ui.components.sections.errorSection
import com.pointlessapps.filman.ui.components.sections.featuredSection
import com.pointlessapps.filman.ui.components.sections.moviesGridSection
import com.pointlessapps.filman.ui.components.sections.moviesRowSection
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.Event
import com.pointlessapps.filman.ui.core.Event.ScrollToTopEvent
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalEventDispatcher
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.CONTINUE_WATCHING
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.Companion.moviesRowPrefix
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FEATURED
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun HomeScreen(
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: HomeViewModel = koinViewModel(),
) {
    var initiallyLoaded by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchResultsFocusRequester = remember { FocusRequester() }
    val returnFocusRequester = remember { FocusRequester() }
    val featuredFirstItemFocusRequester = remember { FocusRequester() }
    val continueWatchingFirstItemFocusRequester = remember { FocusRequester() }
    val favoritesFirstItemFocusRequester = remember { FocusRequester() }

    var lastFocusedItemIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyGridState()
    var focusOnContentWhenLoaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initiallyLoaded) {
            initiallyLoaded = true
            viewModel.onEvent(HomeEvent.LoadHomeData)
        }
    }

    val eventDispatcher = LocalEventDispatcher.current
    LaunchedEffect(eventDispatcher) {
        eventDispatcher.events.collect { event ->
            if (event is ScrollToTopEvent) {
                if (listState.firstVisibleItemIndex > 0) {
                    listState.scrollToItem(1)
                }
                listState.animateScrollToItem(0)
            } else if (event is Event.FocusOnContent) {
                focusOnContentWhenLoaded = true
            }
        }
    }

    LifecycleResumeEffect(state.isLoading) {
        if (!state.isLoading) {
            coroutineScope.launch {
                delay(100.milliseconds)
                if (lastFocusedItemIds.isNotEmpty()) {
                    returnFocusRequester.requestFocus()
                    lastFocusedItemIds = lastFocusedItemIds.dropLast(1)
                } else if (focusOnContentWhenLoaded) {
                    delay(100.milliseconds)
                    contentFocusRequester.requestFocus()
                }
            }
        }

        onPauseOrDispose { }
    }

    var wasMenuOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.overlayMenuData) {
        if (state.overlayMenuData != null) {
            wasMenuOpen = true
        } else if (wasMenuOpen) {
            wasMenuOpen = false
            delay(100.milliseconds)
            if (lastFocusedItemIds.isNotEmpty()) {
                returnFocusRequester.requestFocus()
                lastFocusedItemIds = lastFocusedItemIds.dropLast(1)
            }
        }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.ScrollToTop -> listState.scrollToItem(0)
            is HomeEffect.NavigateToAuth -> onNavigateTo(Route.Login())
            is HomeEffect.NavigateToDetails ->
                onNavigateTo(Route.Details(effect.url, effect.autoplay, effect.episodeUrl))

            is HomeEffect.OverrideFocus -> {
                lastFocusedItemIds = if (lastFocusedItemIds.isNotEmpty()) {
                    lastFocusedItemIds.dropLast(1) + effect.itemId
                } else {
                    listOf(effect.itemId)
                }
            }

            is HomeEffect.FocusFirstGridItem -> {
                delay(100.milliseconds)
                lastFocusedItemIds = emptyList()
                searchResultsFocusRequester.requestFocus()
            }
        }
    }

    Crossfade(
        targetState = state.isLoading,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            HomeScreenContent(
                state = state,
                listState = listState,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                paddingValues = paddingValues,
                onItemClicked = { sectionPrefix, url, autoplay, episodeUrl ->
                    lastFocusedItemIds = lastFocusedItemIds + "$sectionPrefix${episodeUrl ?: url}"
                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url, autoplay, episodeUrl))
                },
                onSetLastFocusedItemId = { id ->
                    lastFocusedItemIds = lastFocusedItemIds + id
                },
                focusRestorationState = FocusRestorationState(
                    focusRequester = returnFocusRequester,
                    lastFocusedItemKeys = lastFocusedItemIds,
                ),
                firstItemFocusRequester = searchResultsFocusRequester,
                featuredFirstItemFocusRequester = featuredFirstItemFocusRequester,
                continueWatchingFirstItemFocusRequester = continueWatchingFirstItemFocusRequester,
                favoritesFirstItemFocusRequester = favoritesFirstItemFocusRequester,
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
private fun HomeScreenContent(
    state: HomeState,
    listState: LazyGridState,
    onEvent: (FilmanEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    onItemClicked: (sectionPrefix: String, url: String, autoplay: Boolean, episodeUrl: String?) -> Unit,
    onSetLastFocusedItemId: (String) -> Unit,
    focusRestorationState: FocusRestorationState,
    firstItemFocusRequester: FocusRequester,
    featuredFirstItemFocusRequester: FocusRequester,
    continueWatchingFirstItemFocusRequester: FocusRequester,
    favoritesFirstItemFocusRequester: FocusRequester,
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
            errorSection(
                errorMessage = state.errorMessage,
                paddingValues = paddingValues,
                onRefresh = { onEvent(HomeEvent.LoadHomeData) },
            )

            if (state.errorMessage != null) return@LazyVerticalGrid

            featuredSection(
                items = state.featuredItems,
                paddingValues = paddingValues,
                onItemClicked = { onItemClicked(FEATURED.prefix, it.url, false, null) },
                onItemLongClicked = { item ->
                    onSetLastFocusedItemId("${FEATURED.prefix}${item.url}")
                    onEvent(BaseEvent.OpenContextMenu(movie = item))
                },
                firstItemFocusRequester = featuredFirstItemFocusRequester,
            )

            if (state.featuredItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(
                        Modifier.padding(top = paddingValues.calculateTopPadding()),
                    )
                }
            }

            continueWatchingSection(
                items = state.progressItems,
                onItemClicked = { item ->
                    val parentUrl = item.parentUrl
                    if (parentUrl != null && parentUrl != item.url) {
                        val episodeUrl = if (item is ProgressItem.NextEpisode) null else item.url
                        onItemClicked(CONTINUE_WATCHING.prefix, parentUrl, true, episodeUrl)
                    } else {
                        onItemClicked(CONTINUE_WATCHING.prefix, item.url, true, null)
                    }
                },
                onItemLongClicked = { item ->
                    onSetLastFocusedItemId("${CONTINUE_WATCHING.prefix}${item.url}")
                    val watchOption = if (item is ProgressItem.Watched) {
                        ContextMenuOption.MARK_AS_NOT_WATCHED
                    } else {
                        ContextMenuOption.MARK_AS_WATCHED
                    }
                    onEvent(
                        BaseEvent.OpenContextMenu(
                            movie = MovieItem(
                                url = item.url,
                                titlePl = item.displayTitle,
                                posterUrl = item.posterUrl,
                                seriesUrl = item.parentUrl,
                                seasonNumber = item.season,
                                episodeNumber = item.episode,
                            ),
                            options = setOfNotNull(
                                ContextMenuOption.REMOVE_FROM_CONTINUE_WATCHING,
                                watchOption,
                                ContextMenuOption.FAVORITES.takeIf {
                                    // Don't allow to favourite an episode
                                    item.parentUrl == item.url
                                },
                            ),
                        ),
                    )
                },
                firstItemFocusRequester = continueWatchingFirstItemFocusRequester,
            )

            moviesRowSection(
                title = resources.getString(R.string.home_favorites),
                items = state.favorites,
                onItemClicked = {
                    onItemClicked(
                        moviesRowPrefix(resources.getString(R.string.home_favorites)),
                        it.url,
                        false,
                        null,
                    )
                },
                onItemLongClicked = { item ->
                    onSetLastFocusedItemId(
                        "${moviesRowPrefix(resources.getString(R.string.home_favorites))}${item.url}",
                    )
                    onEvent(BaseEvent.OpenContextMenu(movie = item))
                },
                firstItemFocusRequester = favoritesFirstItemFocusRequester,
            )

            state.moviesSections.forEachIndexed { index, section ->
                moviesGridSection(
                    title = resources.getString(section.title),
                    items = section.movies,
                    isLoadingNextPage = false,
                    onItemClicked = { onItemClicked(RECOMMENDED.prefix, it.url, false, null) },
                    onItemLongClicked = { item ->
                        onSetLastFocusedItemId("${RECOMMENDED.prefix}${item.url}")
                        onEvent(BaseEvent.OpenContextMenu(movie = item))
                    },
                    onLoadNextPageRequest = { },
                    showLoadMoreButton = false,
                    onShowMoreClicked = { },
                    firstItemFocusRequester = if (index == 0) firstItemFocusRequester else null,
                )
            }
        }
    }
}
