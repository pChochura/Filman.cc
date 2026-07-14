package com.example.filman.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.example.filman.R
import com.example.filman.Route
import com.example.filman.ui.components.FilmanNavigationBar
import com.example.filman.ui.components.FilmanNavigationItem
import com.example.filman.ui.components.FilmanOverlayMenu
import com.example.filman.ui.components.FilmanScaffold
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.home.sections.continueWatchingSection
import com.example.filman.ui.home.sections.featuredSection
import com.example.filman.ui.home.sections.moviesGridSection
import com.example.filman.ui.home.sections.moviesRowSection
import com.example.filman.ui.home.sections.searchBarSection
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onNavigateTo: (Route) -> Unit,
) {
    var initiallyLoaded by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (!initiallyLoaded) {
            viewModel.onEvent(HomeEvent.LoadHomeData)
            initiallyLoaded = true
        }
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            is HomeEffect.FocusFeaturedSection -> {
                delay(100.milliseconds)
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                    content = { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) },
                )
            } else {
                HomeScreenContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    contentFocusRequester = contentFocusRequester,
                    paddingValues = paddingValues,
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
    onEvent: (HomeEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
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
            onItemClicked = { onEvent(HomeEvent.OpenMovieDetails(it.url)) },
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
                onItemClicked = { onEvent(HomeEvent.OpenMovieDetails(it.url)) },
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
                onItemClicked = { onEvent(HomeEvent.OpenMovieDetails(it.url)) },
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

        moviesGridSection(
            title = if (state.showSearchBar) null else R.string.home_recommended,
            items = state.movies,
            isLoadingNextPage = state.isLoadingNextPage,
            onItemClicked = { onEvent(HomeEvent.OpenMovieDetails(it.url)) },
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
