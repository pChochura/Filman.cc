package com.example.filman.ui.home

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
import com.example.filman.R
import com.example.filman.Route
import com.example.filman.ui.components.FilmanFullscreenLoader
import com.example.filman.ui.components.FilmanNavigationBar
import com.example.filman.ui.components.FilmanNavigationItem
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.components.FilmanScaffold
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.FocusRestorationState
import com.example.filman.ui.core.LocalFocusRestorationState
import com.example.filman.ui.home.sections.continueWatchingSection
import com.example.filman.ui.home.sections.featuredSection
import com.example.filman.ui.home.sections.moviesGridSection
import com.example.filman.ui.home.sections.moviesRowSection
import com.example.filman.ui.home.sections.searchBarSection
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId.CONTINUE_WATCHING
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId.Companion.moviesRowPrefix
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId.FEATURED
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId.RECOMMENDED
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun HomeScreen(
    onNavigateTo: (Route) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    var initiallyLoaded by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contentFocusRequester = remember { FocusRequester() }
    val returnFocusRequester = remember { FocusRequester() }
    var lastFocusedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (!initiallyLoaded) {
            initiallyLoaded = true
            viewModel.onEvent(HomeEvent.LoadHomeData)
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
            is HomeEffect.ScrollToTop -> listState.scrollToItem(0)
            is HomeEffect.FocusFeaturedSection -> {
                delay(100.milliseconds)
                lastFocusedItemId = null
                contentFocusRequester.requestFocus()
            }

            is HomeEffect.NavigateToAuth -> onNavigateTo(Route.Auth)
            is HomeEffect.NavigateToDetails -> onNavigateTo(Route.Details(effect.url))
        }
    }

    FilmanScaffold(
        navigationTopBar = {
            FilmanNavigationBar(
                currentRouteProvider = state::route,
                onRouteChanged = { viewModel.onEvent(HomeEvent.OnPageSelected(it)) },
                items = listOf(
                    FilmanNavigationItem.Icon(
                        icon = R.drawable.ic_search,
                        contentDescription = R.string.home_search,
                        route = Route.Home.Search,
                    ),
                    FilmanNavigationItem.Text(
                        title = R.string.home_tab_home,
                        route = Route.Home.Home,
                    ),
                    FilmanNavigationItem.Text(
                        title = R.string.home_tab_movies,
                        route = Route.Home.Movies,
                    ),
                    FilmanNavigationItem.Text(
                        title = R.string.home_tab_series,
                        route = Route.Home.TvShows,
                    ),
                    FilmanNavigationItem.Text(
                        title = R.string.home_tab_kids,
                        route = Route.Home.ForKids,
                    ),
                ),
                contentFocusRequester = contentFocusRequester,
            )
        },
    ) { paddingValues ->
        AnimatedContent(
            targetState = state.isLoading,
            contentAlignment = Alignment.Center,
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
                    onItemClicked = { sectionPrefix, url ->
                        lastFocusedItemId = "$sectionPrefix$url"
                        viewModel.onEvent(HomeEvent.OpenMovieDetails(url))
                    },
                    focusRestorationState = FocusRestorationState(
                        focusRequester = returnFocusRequester,
                        lastFocusedItemKey = lastFocusedItemId,
                    ),
                )
            }
        }
    }

    state.overlayMenuData?.let { data ->
        FilmanOverlayMenu(
            title = data.title,
            items = data.items,
            onDismissRequest = { viewModel.onEvent(HomeEvent.CloseContextMenu) },
        )
    }
}

@Composable
private fun HomeScreenContent(
    state: HomeState,
    listState: LazyListState,
    onEvent: (HomeEvent) -> Unit,
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
            if (state.showSearchBar) {
                searchBarSection(
                    paddingValues = paddingValues,
                    onSearchRequested = { onEvent(HomeEvent.LoadSearchData(it)) },
                )
            }

            featuredSection(
                items = state.featuredItems,
                paddingValues = paddingValues,
                onItemClicked = { onItemClicked(FEATURED.prefix, it.url) },
                onItemLongClicked = { item ->
                    onEvent(
                        HomeEvent.OpenContextMenu(
                            title = item.titlePl,
                            url = item.url,
                            posterUrl = item.posterUrl,
                            isInContinueWatching = false,
                        ),
                    )
                },
            )

            if (state.featuredItems.isEmpty() && !state.showSearchBar) {
                item { Spacer(Modifier.padding(top = paddingValues.calculateTopPadding())) }
            }

            if (state.showContinueWatching) {
                continueWatchingSection(
                    items = state.progressItems,
                    onItemClicked = { onItemClicked(CONTINUE_WATCHING.prefix, it.url) },
                    onItemLongClicked = { item ->
                        onEvent(
                            HomeEvent.OpenContextMenu(
                                title = item.titlePl,
                                url = item.url,
                                posterUrl = item.posterUrl,
                                isInContinueWatching = true,
                            ),
                        )
                    },
                )
            }

            if (state.showFavourites) {
                moviesRowSection(
                    title = R.string.home_favorites,
                    items = state.favorites,
                    onItemClicked = {
                        onItemClicked(moviesRowPrefix(R.string.home_favorites), it.url)
                    },
                    onItemLongClicked = { item ->
                        onEvent(
                            HomeEvent.OpenContextMenu(
                                title = item.titlePl,
                                url = item.url,
                                posterUrl = item.posterUrl,
                                isInContinueWatching = false,
                            ),
                        )
                    },
                )
            }

            state.moviesSections.forEach { section ->
                moviesGridSection(
                    title = section.title,
                    items = section.movies,
                    isLoadingNextPage = state.isLoadingNextPage,
                    onItemClicked = { onItemClicked(RECOMMENDED.prefix, it.url) },
                    onItemLongClicked = { item ->
                        onEvent(
                            HomeEvent.OpenContextMenu(
                                title = item.titlePl,
                                url = item.url,
                                posterUrl = item.posterUrl,
                                isInContinueWatching = false,
                            ),
                        )
                    },
                    onLoadNextPageRequest = { onEvent(HomeEvent.LoadNextPageData) },
                )
            }
        }
    }
}
