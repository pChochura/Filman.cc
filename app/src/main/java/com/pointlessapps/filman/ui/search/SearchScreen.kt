package com.pointlessapps.filman.ui.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.sections.MoviesGridItem
import com.pointlessapps.filman.ui.components.sections.errorSection
import com.pointlessapps.filman.ui.components.sections.moviesGridSection
import com.pointlessapps.filman.ui.components.sections.searchBarSection
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.Event.ScrollToTopEvent
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalEventDispatcher
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SearchScreen(
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: SearchViewModel = koinViewModel(),
) {
    var initiallyLoaded by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchResultsFocusRequester = remember { FocusRequester() }
    val returnFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    val historyFocusRequesters = remember(state.searchHistory) {
        state.searchHistory.associateWith { FocusRequester() }
    }
    val currentHistoryFocusRequesters by rememberUpdatedState(historyFocusRequesters)
    var lastFocusedItemIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (!initiallyLoaded) {
            initiallyLoaded = true
            viewModel.onEvent(SearchEvent.LoadHomeData)
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
                }
            }
        }

        onPauseOrDispose { }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is SearchEffect.ScrollToTop -> listState.scrollToItem(0)
            is SearchEffect.NavigateToAuth -> onNavigateTo(Route.Login(replaceCurrentRoute = false))
            is SearchEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
            is SearchEffect.FocusHistoryItem -> {
                coroutineScope.launch {
                    delay(100.milliseconds)
                    if (effect.query == null) {
                        textFieldFocusRequester.requestFocus()
                    } else {
                        currentHistoryFocusRequesters[effect.query]?.requestFocus()
                    }
                }
            }

            is SearchEffect.FocusSearchResults -> {
                coroutineScope.launch {
                    delay(100.milliseconds)
                    searchResultsFocusRequester.requestFocus()
                }
            }
        }
    }

    Crossfade(
        targetState = state.isLoading,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            SearchScreenContent(
                state = state,
                listState = listState,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                paddingValues = paddingValues,
                onSearchRequested = {
                    lastFocusedItemIds = lastFocusedItemIds + "search_bar"
                    viewModel.onEvent(SearchEvent.LoadSearchData(it))
                },
                onItemClicked = { sectionPrefix, url ->
                    lastFocusedItemIds = lastFocusedItemIds + "$sectionPrefix$url"
                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url))
                },
                onSetLastFocusedItemId = {
                    lastFocusedItemIds = lastFocusedItemIds + it
                },
                focusRestorationState = FocusRestorationState(
                    focusRequester = returnFocusRequester,
                    lastFocusedItemKeys = lastFocusedItemIds,
                ),
                searchResultsFocusRequester = searchResultsFocusRequester,
                textFieldFocusRequester = textFieldFocusRequester,
                historyFocusRequesters = historyFocusRequesters,
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
private fun SearchScreenContent(
    state: SearchState,
    listState: LazyGridState,
    onEvent: (FilmanEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    onSearchRequested: (String) -> Unit,
    onItemClicked: (sectionPrefix: String, url: String) -> Unit,
    onSetLastFocusedItemId: (String) -> Unit,
    focusRestorationState: FocusRestorationState,
    searchResultsFocusRequester: FocusRequester,
    textFieldFocusRequester: FocusRequester,
    historyFocusRequesters: Map<String, FocusRequester>,
) {
    val searchFieldState = rememberTextFieldState(initialText = state.query)
    val resources = LocalResources.current

    val leftItemFocusRequesters = remember(state.moviesSections) {
        state.moviesSections.associate { it.title to FocusRequester() }
    }

    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }
            .collectLatest { query ->
                delay(500.milliseconds)
                if (query.isNotBlank()) {
                    onEvent(SearchEvent.LoadSearchData(query))
                } else if (query.isEmpty()) {
                    onEvent(SearchEvent.ClearSearch)
                }
            }
    }

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
            searchBarSection(
                searchFieldState = searchFieldState,
                textFieldFocusRequester = textFieldFocusRequester,
                historyFocusRequesters = historyFocusRequesters,
                paddingValues = paddingValues,
                showCategories = state.errorMessage == null &&
                        !state.isLoadingNextPage &&
                        state.moviesSections.isEmpty(),
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                searchHistory = state.searchHistory,
                onCategoryClicked = { onEvent(SearchEvent.LoadSearchDataByCategory(it)) },
                onSearchRequested = { onSearchRequested(it) },
                onClearSearch = { onEvent(SearchEvent.ClearSearch) },
                onHistoryItemLongClicked = { onEvent(SearchEvent.OpenSearchHistoryContextMenu(it)) },
                onClearAllHistoryClicked = { onEvent(SearchEvent.ClearAllSearchHistory) },
            )

            errorSection(
                errorMessage = state.errorMessage,
                paddingValues = PaddingValues(),
                onRefresh = { onEvent(SearchEvent.RetrySearch) },
            )

            if (state.errorMessage != null) return@LazyVerticalGrid

            state.moviesSections.forEachIndexed { index, section ->
                val leftItemFocusRequester = leftItemFocusRequesters[section.title]
                moviesGridSection(
                    title = resources.getString(section.title),
                    items = section.movies,
                    isLoadingNextPage = state.isLoadingNextPage,
                    onItemClicked = { item ->
                        when (item) {
                            is MoviesGridItem.Single -> {
                                onItemClicked(RECOMMENDED.prefix, item.movieItem.url)
                            }
                            is MoviesGridItem.Group -> {
                                onSetLastFocusedItemId("${RECOMMENDED.prefix}${item.movieItem.url}")
                                onEvent(SearchEvent.OpenGroupSourcesMenu(item))
                            }
                        }
                    },
                    onItemLongClicked = { item ->
                        onSetLastFocusedItemId("${RECOMMENDED.prefix}${item.movieItem.url}")
                        onEvent(BaseEvent.OpenContextMenu(movie = item.movieItem))
                    },
                    onLoadNextPageRequest = { },
                    showLoadMoreButton = section.hasMore,
                    onShowMoreClicked = {
                        leftItemFocusRequester?.requestFocus()
                        onEvent(SearchEvent.LoadMoreForSection(section.title))
                    },
                    firstItemFocusRequester = if (index == 0) searchResultsFocusRequester else null,
                    leftItemFocusRequester = leftItemFocusRequester,
                )
            }
        }
    }
}
