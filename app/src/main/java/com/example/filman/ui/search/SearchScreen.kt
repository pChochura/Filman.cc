package com.example.filman.ui.search

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
import com.example.filman.Route
import com.example.filman.ui.base.BaseEvent
import com.example.filman.ui.base.FilmanEvent
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.components.sections.errorSection
import com.example.filman.ui.components.sections.moviesGridSection
import com.example.filman.ui.components.sections.searchBarSection
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.Event.ScrollToTopEvent
import com.example.filman.ui.core.FocusRestorationState
import com.example.filman.ui.core.LocalEventDispatcher
import com.example.filman.ui.core.LocalFocusRestorationState
import com.example.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
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
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
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

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is SearchEffect.ScrollToTop -> listState.scrollToItem(0)
            is SearchEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
            is SearchEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
            is SearchEffect.FocusFirstGridItem -> {
                delay(100.milliseconds)
                lastFocusedItemId = null
                searchResultsFocusRequester.requestFocus()
            }
        }
    }

    AnimatedContent(
        targetState = state.isLoading,
        contentAlignment = Alignment.Center,
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
                onItemClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url))
                },
                focusRestorationState = FocusRestorationState(
                    focusRequester = returnFocusRequester,
                    lastFocusedItemKey = lastFocusedItemId,
                ),
                searchResultsFocusRequester = searchResultsFocusRequester,
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
    onItemClicked: (sectionPrefix: String, url: String) -> Unit,
    focusRestorationState: FocusRestorationState,
    searchResultsFocusRequester: FocusRequester,
) {
    val resources = LocalResources.current

    val leftItemFocusRequesters = remember(state.moviesSections) {
        state.moviesSections.associate { it.title to FocusRequester() }
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
                paddingValues = paddingValues,
                showCategories = state.errorMessage == null &&
                        !state.isLoadingNextPage &&
                        state.moviesSections.isEmpty(),
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategoryClicked = { onEvent(SearchEvent.LoadSearchDataByCategory(it)) },
                onSearchRequested = { onEvent(SearchEvent.LoadSearchData(it)) },
                onClearSearch = { onEvent(SearchEvent.ClearSearch) },
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
