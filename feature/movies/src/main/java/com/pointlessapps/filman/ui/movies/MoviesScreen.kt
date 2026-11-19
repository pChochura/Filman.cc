package com.pointlessapps.filman.ui.movies

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.LoadingMoreFooter
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.emptyFilterResultsSection
import com.pointlessapps.filman.ui.components.sections.errorSection
import com.pointlessapps.filman.ui.components.sections.featuredSection
import com.pointlessapps.filman.ui.components.sections.filterActionSection
import com.pointlessapps.filman.ui.components.sections.moviesGridSection
import com.pointlessapps.filman.ui.components.sections.staleBannerSection
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.Event
import com.pointlessapps.filman.ui.core.Event.ScrollToTopEvent
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalEventDispatcher
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FEATURED
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.FILTER_BAR
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MoviesScreen(
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: MoviesViewModel = koinViewModel(),
) {
    var initiallyLoaded by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val returnFocusRequester = remember { FocusRequester() }
    var lastFocusedItemIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (!initiallyLoaded) {
            initiallyLoaded = true
            viewModel.onEvent(MoviesEvent.LoadHomeData)
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
            } else if (event is Event.ReloadData) {
                if (state.filterState.isActive) {
                    viewModel.onEvent(MoviesEvent.UpdateFilter(state.filterState))
                } else {
                    viewModel.onEvent(MoviesEvent.LoadHomeData)
                }
            }
        }
    }

    LifecycleResumeEffect(state.isLoading) {
        if (!state.isLoading) {
            coroutineScope.launch {
                delay(100.milliseconds)
                if (lastFocusedItemIds.isNotEmpty()) {
                    runCatching { returnFocusRequester.requestFocus() }
                    lastFocusedItemIds = lastFocusedItemIds.dropLast(1)
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
                runCatching { returnFocusRequester.requestFocus() }
                lastFocusedItemIds = lastFocusedItemIds.dropLast(1)
            }
        }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is MoviesEffect.ScrollToTop -> listState.scrollToItem(0)
            is MoviesEffect.NavigateToAuth -> onNavigateTo(Route.Login())
            is MoviesEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.request))
        }
    }

    Crossfade(
        targetState = state.isLoading,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            MoviesScreenContent(
                state = state,
                listState = listState,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                paddingValues = paddingValues,
                onItemClicked = { sectionPrefix, item ->
                    val url = item.movieItem.url
                    lastFocusedItemIds = lastFocusedItemIds + "$sectionPrefix$url"

                    val request = item.movieItem.detailsRequest

                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url = url, request = request))
                },
                onSetLastFocusedItemId = { id ->
                    lastFocusedItemIds = lastFocusedItemIds + id
                },
                focusRestorationState =
                    FocusRestorationState(
                        focusRequester = returnFocusRequester,
                        lastFocusedItemKeys = lastFocusedItemIds,
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
private fun MoviesScreenContent(
    state: MoviesState,
    listState: LazyGridState,
    onEvent: (FilmanEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    onItemClicked: (sectionPrefix: String, item: MoviesGridItem) -> Unit,
    onSetLastFocusedItemId: (String) -> Unit,
    focusRestorationState: FocusRestorationState,
) {
    val resources = LocalResources.current
    val progressMapState = rememberUpdatedState(state.shared.progressMap)
    val filteredLeftFocusRequester = remember(state.filteredSection) { FocusRequester() }

    val leftItemFocusRequesters =
        remember(state.moviesSections) {
            state.moviesSections.associate { it.title to FocusRequester() }
        }

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
                paddingValues = paddingValues,
                onRefresh = { onEvent(MoviesEvent.LoadHomeData) },
            )

            if (state.errorMessage != null) return@LazyVerticalGrid

            if (!state.filterState.isActive) {
                featuredSection(
                    items = state.featuredItems,
                    paddingValues = paddingValues,
                    onItemClicked = { onItemClicked(FEATURED.prefix, MoviesGridItem.Single(it)) },
                    onItemLongClicked = { item ->
                        onSetLastFocusedItemId("${FEATURED.prefix}${item.url}")
                        onEvent(BaseEvent.OpenContextMenu(movie = item))
                    },
                    progressProvider = { progressMapState.value },
                )

                if (state.featuredItems.isEmpty()) {
                    item(
                        key = "top_spacer",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "Spacer",
                    ) {
                        Spacer(
                            Modifier.padding(top = paddingValues.calculateTopPadding()),
                        )
                    }
                }
            } else {
                item(
                    key = "filter_top_spacer",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "Spacer",
                ) {
                    Spacer(
                        Modifier.padding(top = paddingValues.calculateTopPadding()),
                    )
                }
            }

            staleBannerSection(isShowingStaleData = state.isShowingStaleData)

            filterActionSection(
                hasActiveFilters = state.filterState.isActive,
                onOpenFilterMenu = {
                    onSetLastFocusedItemId("${FILTER_BAR.prefix}trigger")
                    onEvent(MoviesEvent.OpenFilterMenu)
                },
                onResetFilters = { onEvent(MoviesEvent.ResetFilters) },
            )

            if (state.filterState.isActive) {
                if (state.isFiltering) {
                    item(
                        key = "filter_loading",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "LoadingMoreFooter",
                    ) {
                        LoadingMoreFooter()
                    }
                } else if (state.filterErrorMessage != null) {
                    errorSection(
                        errorMessage = state.filterErrorMessage,
                        paddingValues = PaddingValues(),
                        onRefresh = { onEvent(MoviesEvent.UpdateFilter(state.filterState)) },
                    )
                } else {
                    val filteredSection = state.filteredSection
                    if (filteredSection != null && filteredSection.movies.isEmpty() && !filteredSection.hasMore) {
                        emptyFilterResultsSection(
                            onResetFilters = { onEvent(MoviesEvent.ResetFilters) },
                        )
                    } else if (filteredSection != null) {
                        moviesGridSection(
                            title = resources.getString(filteredSection.title),
                            items = filteredSection.movies,
                            isLoadingNextPage = state.isLoadingNextPage,
                            onItemClicked = { onItemClicked(RECOMMENDED.prefix, it) },
                            onItemLongClicked = { item ->
                                onSetLastFocusedItemId("${RECOMMENDED.prefix}${item.movieItem.url}")
                                onEvent(BaseEvent.OpenContextMenu(movie = item.movieItem))
                            },
                            onLoadNextPageRequest = { },
                            showLoadMoreButton = filteredSection.hasMore,
                            onShowMoreClicked = {
                                if (filteredSection.movies.isNotEmpty()) {
                                    filteredLeftFocusRequester.requestFocus()
                                }
                                onEvent(MoviesEvent.LoadMoreForSection(filteredSection.title))
                            },
                            leftItemFocusRequester = filteredLeftFocusRequester,
                            progressProvider = { progressMapState.value },
                        )
                    }
                }
            } else {
                state.moviesSections.forEach { section ->
                    val leftItemFocusRequester = leftItemFocusRequesters[section.title]
                    moviesGridSection(
                        title = resources.getString(section.title),
                        items = section.movies,
                        isLoadingNextPage = state.isLoadingNextPage,
                        onItemClicked = { onItemClicked(RECOMMENDED.prefix, it) },
                        onItemLongClicked = { item ->
                            onSetLastFocusedItemId("${RECOMMENDED.prefix}${item.movieItem.url}")
                            onEvent(BaseEvent.OpenContextMenu(movie = item.movieItem))
                        },
                        onLoadNextPageRequest = { },
                        showLoadMoreButton = section.hasMore,
                        onShowMoreClicked = {
                            if (section.movies.isNotEmpty()) {
                                leftItemFocusRequester?.requestFocus()
                            }
                            onEvent(MoviesEvent.LoadMoreForSection(section.title))
                        },
                        firstItemFocusRequester = null,
                        leftItemFocusRequester = leftItemFocusRequester,
                        progressProvider = { progressMapState.value },
                    )
                }
            }
        }
    }
}
