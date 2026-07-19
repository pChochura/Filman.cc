package com.example.filman.ui.tvshows

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.components.sections.errorSection
import com.example.filman.ui.components.sections.featuredSection
import com.example.filman.ui.components.sections.moviesGridSection
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.Event.ScrollToTopEvent
import com.example.filman.ui.core.FocusRestorationState
import com.example.filman.ui.core.LocalEventDispatcher
import com.example.filman.ui.core.LocalFocusRestorationState
import com.example.filman.ui.core.SectionFocusRestorationId.FEATURED
import com.example.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun TvShowsScreen(
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: TvShowsViewModel = koinViewModel(),
) {
    var initiallyLoaded by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val returnFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        if (!initiallyLoaded) {
            initiallyLoaded = true
            viewModel.onEvent(TvShowsEvent.LoadHomeData)
        }
    }

    val eventDispatcher = LocalEventDispatcher.current
    LaunchedEffect(eventDispatcher) {
        eventDispatcher.events.collect { event ->
            if (event is ScrollToTopEvent) {
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
            is TvShowsEffect.ScrollToTop -> listState.scrollToItem(0)
            is TvShowsEffect.FocusFeaturedSection -> {
                delay(100.milliseconds)
                lastFocusedItemId = null
                contentFocusRequester.requestFocus()
            }

            is TvShowsEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
            is TvShowsEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
        }
    }

    AnimatedContent(
        targetState = state.isLoading,
        contentAlignment = Alignment.Center,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            TvShowsScreenContent(
                state = state,
                listState = listState,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                paddingValues = paddingValues,
                onItemClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(TvShowsEvent.OpenMovieDetails(url))
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
            onDismissRequest = { viewModel.onEvent(TvShowsEvent.CloseContextMenu) },
        )
    }
}

@Composable
private fun TvShowsScreenContent(
    state: TvShowsState,
    listState: LazyGridState,
    onEvent: (TvShowsEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
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
            errorSection(
                errorMessage = state.errorMessage,
                paddingValues = paddingValues,
                onRefresh = { onEvent(TvShowsEvent.LoadHomeData) },
            )

            if (state.errorMessage != null) return@LazyVerticalGrid

            featuredSection(
                items = state.featuredItems,
                paddingValues = paddingValues,
                onItemClicked = { onItemClicked(FEATURED.prefix, it.url) },
                onItemLongClicked = { item ->
                    onEvent(
                        TvShowsEvent.OpenContextMenu(
                            title = item.titlePl,
                            url = item.url,
                            posterUrl = item.posterUrl,
                        ),
                    )
                },
            )

            if (state.featuredItems.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Spacer(
                        Modifier.padding(top = paddingValues.calculateTopPadding()),
                    )
                }
            }

            state.moviesSections.forEach { section ->
                moviesGridSection(
                    title = resources.getString(section.title),
                    items = section.movies,
                    isLoadingNextPage = state.isLoadingNextPage,
                    onItemClicked = { onItemClicked(RECOMMENDED.prefix, it.url) },
                    onItemLongClicked = { item ->
                        onEvent(
                            TvShowsEvent.OpenContextMenu(
                                title = item.titlePl,
                                url = item.url,
                                posterUrl = item.posterUrl,
                            ),
                        )
                    },
                    onLoadNextPageRequest = { onEvent(TvShowsEvent.LoadNextPageData) },
                    showLoadMoreButton = false,
                    onShowMoreClicked = { },
                    firstItemFocusRequester = null,
                )
            }
        }
    }
}
