package com.pointlessapps.filman.ui.search

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pointlessapps.filman.Route
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.components.FilmanFullscreenLoader
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.core.CollectEffect
import com.pointlessapps.filman.ui.core.Event
import com.pointlessapps.filman.ui.core.Event.ScrollToTopEvent
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalEventDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SearchScreen(
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
    val historyFocusRequesters =
        remember(state.searchHistory) {
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
            } else if (event is Event.ReloadData) {
                if (state.query.isNotBlank() || state.filterState.isActive) {
                    viewModel.onEvent(SearchEvent.RetrySearch)
                } else {
                    viewModel.onEvent(SearchEvent.LoadHomeData)
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
            is SearchEffect.ScrollToTop -> {
                listState.scrollToItem(0)
            }

            is SearchEffect.NavigateToAuth -> {
                onNavigateTo(Route.Login(replaceCurrentRoute = false))
            }

            is SearchEffect.NavigateToDetails -> {
                onNavigateTo(Route.Details(effect.request))
            }

            is SearchEffect.FocusHistoryItem -> {
                coroutineScope.launch {
                    delay(100.milliseconds)
                    if (effect.query == null) {
                        runCatching { textFieldFocusRequester.requestFocus() }
                    } else {
                        runCatching { currentHistoryFocusRequesters[effect.query]?.requestFocus() }
                    }
                }
            }

            is SearchEffect.FocusSearchResults -> {
                coroutineScope.launch {
                    delay(100.milliseconds)
                    runCatching { searchResultsFocusRequester.requestFocus() }
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
                    viewModel.onEvent(SearchEvent.LoadSearchData(it, force = true))
                },
                onItemClicked = { sectionPrefix, url, request ->
                    lastFocusedItemIds = lastFocusedItemIds + "$sectionPrefix$url"
                    viewModel.onEvent(BaseEvent.OpenMovieDetails(url = url, request = request))
                },
                onSetLastFocusedItemId = {
                    lastFocusedItemIds = lastFocusedItemIds + it
                },
                focusRestorationState =
                    FocusRestorationState(
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
