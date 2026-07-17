package com.example.filman.ui.forkids

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.filman.ui.core.FocusRestorationState
import com.example.filman.ui.core.LocalFocusRestorationState
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId.FEATURED
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId.RECOMMENDED
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ForKidsScreen(
    onNavigateTo: (Route) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    viewModel: ForKidsViewModel = koinViewModel(),
) {
    var initiallyLoaded by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val returnFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (!initiallyLoaded) {
            initiallyLoaded = true
            viewModel.onEvent(ForKidsEvent.LoadHomeData)
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
            is ForKidsEffect.ScrollToTop -> listState.scrollToItem(0)
            is ForKidsEffect.FocusFeaturedSection -> {
                delay(100.milliseconds)
                lastFocusedItemId = null
                contentFocusRequester.requestFocus()
            }

            is ForKidsEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
            is ForKidsEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
        }
    }

    AnimatedContent(
        targetState = state.isLoading,
        contentAlignment = Alignment.Center,
    ) { isLoading ->
        if (isLoading) {
            FilmanFullscreenLoader()
        } else {
            ForKidsScreenContent(
                state = state,
                listState = listState,
                onEvent = viewModel::onEvent,
                contentFocusRequester = contentFocusRequester,
                paddingValues = paddingValues,
                onItemClicked = { sectionPrefix, url ->
                    lastFocusedItemId = "$sectionPrefix$url"
                    viewModel.onEvent(ForKidsEvent.OpenMovieDetails(url))
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
            onDismissRequest = { viewModel.onEvent(ForKidsEvent.CloseContextMenu) },
        )
    }
}

@Composable
private fun ForKidsScreenContent(
    state: ForKidsState,
    listState: LazyListState,
    onEvent: (ForKidsEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    onItemClicked: (sectionPrefix: String, url: String) -> Unit,
    focusRestorationState: FocusRestorationState,
) {
    CompositionLocalProvider(LocalFocusRestorationState provides focusRestorationState) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester),
            contentPadding = PaddingValues(
                bottom = MaterialTheme.spacing.extraLarge,
            ),
        ) {
            errorSection(
                errorMessage = state.errorMessage,
                paddingValues = paddingValues,
                onRefresh = { onEvent(ForKidsEvent.LoadHomeData) },
            )

            if (state.errorMessage != null) return@LazyColumn

            featuredSection(
                items = state.featuredItems,
                paddingValues = paddingValues,
                onItemClicked = { onItemClicked(FEATURED.prefix, it.url) },
                onItemLongClicked = { item ->
                    onEvent(
                        ForKidsEvent.OpenContextMenu(
                            title = item.titlePl,
                            url = item.url,
                            posterUrl = item.posterUrl,
                        ),
                    )
                },
            )

            if (state.featuredItems.isEmpty()) {
                item { Spacer(Modifier.padding(top = paddingValues.calculateTopPadding())) }
            }

            state.moviesSections.forEach { section ->
                moviesGridSection(
                    title = section.title,
                    items = section.movies,
                    isLoadingNextPage = state.isLoadingNextPage,
                    onItemClicked = { onItemClicked(RECOMMENDED.prefix, it.url) },
                    onItemLongClicked = { item ->
                        onEvent(
                            ForKidsEvent.OpenContextMenu(
                                title = item.titlePl,
                                url = item.url,
                                posterUrl = item.posterUrl,
                            ),
                        )
                    },
                    onLoadNextPageRequest = { onEvent(ForKidsEvent.LoadNextPageData) },
                    showLoadMoreButton = false,
                    onShowMoreClicked = { },
                    firstItemFocusRequester = null,
                )
            }
        }
    }
}
