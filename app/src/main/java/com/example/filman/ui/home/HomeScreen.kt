package com.example.filman.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.example.filman.R
import com.example.filman.data.model.FeaturedItem
import com.example.filman.data.model.FilterData
import com.example.filman.data.model.Movie
import com.example.filman.data.model.ProgressItem
import com.example.filman.ui.components.atoms.ButtonStyle
import com.example.filman.ui.components.atoms.FilmanButton
import com.example.filman.ui.components.molecules.SearchBar
import com.example.filman.ui.components.organisms.FiltersOverlay
import com.example.filman.ui.components.templates.DialogOverlayTemplate
import com.example.filman.ui.components.templates.ScreenTemplate
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.home.components.HomeContextMenu
import com.example.filman.ui.home.components.HomeDrawer
import com.example.filman.ui.home.components.categoryTabContent
import com.example.filman.ui.home.components.homeTabContent
import com.example.filman.ui.home.components.searchResultsContent
import com.example.filman.ui.theme.spacing

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onMovieClick: (String) -> Unit,
    onAuthInvalid: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(HomeEvent.LoadHomeData)
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            HomeEffect.NavigateToAuth -> onAuthInvalid()
            is HomeEffect.NavigateToDetails -> onMovieClick(effect.url)
        }
    }

    BackHandler(state.isSearchVisible) {
        viewModel.onEvent(HomeEvent.OnSearchVisibleChanged(false))
    }

    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
) {
    ScreenTemplate(
        isLoading = state.isLoading,
        error = state.error,
        onErrorRetry = { onEvent(HomeEvent.LoadHomeData) },
    ) {
        var contextMenuData by remember { mutableStateOf<ContextMenuData?>(null) }
        var isFiltersVisible by remember { mutableStateOf(false) }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val drawerFocusRequester = remember { FocusRequester() }
        val contentFocusRequester = remember { FocusRequester() }
        val filterFocusRequester = remember { FocusRequester() }

        val isDrawerOpen by remember { derivedStateOf { drawerState.currentValue == DrawerValue.Open } }
        val isOverlayOpen by remember {
            derivedStateOf { isFiltersVisible || contextMenuData != null }
        }

        val onMovieClickStable = remember(onEvent) {
            {
                movie: Movie ->
                onEvent(HomeEvent.OnMovieClick(movie.url))
            }
        }
        val onMovieContextMenuStable = remember {
            {
                movie: Movie ->
                contextMenuData = ContextMenuData(
                    url = movie.url,
                    title = movie.title,
                    posterUrl = movie.posterUrl,
                    isProgress = false,
                )
            }
        }
        val onProgressClickStable = remember(onEvent) {
            {
                item: ProgressItem ->
                onEvent(HomeEvent.OnMovieClick(item.url))
            }
        }
        val onProgressContextMenuStable = remember {
            {
                item: ProgressItem ->
                contextMenuData = ContextMenuData(
                    url = item.url,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    isProgress = true,
                    seriesUrl = item.seriesUrl,
                )
            }
        }

        val items = when (state.selectedTabIndex) {
            1 -> state.moviesList
            2 -> state.seriesList
            3 -> state.kidsList
            else -> emptyList()
        }
        val chunkedCategoryItems = remember(items) { items.chunked(5) }
        val chunkedSearchResults = remember(state.searchResults) { state.searchResults?.chunked(5) }

        // Back navigation logic
        BackHandler(enabled = !isDrawerOpen && !isOverlayOpen && !state.isSearchVisible) {
            if (state.selectedTabIndex != 0) {
                onEvent(HomeEvent.OnTabSelected(0))
            } else {
                drawerFocusRequester.requestFocus()
            }
        }

        BackHandler(enabled = isDrawerOpen && state.selectedTabIndex != 0) {
            contentFocusRequester.requestFocus()
        }

        BackHandler(enabled = isFiltersVisible) {
            isFiltersVisible = false
        }

        BackHandler(enabled = contextMenuData != null) {
            contextMenuData = null
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                HomeDrawer(
                    isSearchVisible = state.isSearchVisible,
                    selectedTabIndex = state.selectedTabIndex,
                    onEvent = onEvent,
                    drawerFocusRequester = drawerFocusRequester,
                    contentFocusRequester = contentFocusRequester,
                )
            },
        ) {
            HomeMainContent(
                isSearchVisible = state.isSearchVisible,
                selectedTabIndex = state.selectedTabIndex,
                searchQuery = state.searchQuery,
                favorites = state.favorites,
                onEvent = onEvent,
                contentFocusRequester = contentFocusRequester,
                drawerFocusRequester = drawerFocusRequester,
                filterFocusRequester = filterFocusRequester,
                contextMenuData = contextMenuData,
                onContextMenuChange = { contextMenuData = it },
                isFiltersVisible = isFiltersVisible,
                onFiltersVisibleChange = { isFiltersVisible = it },
                onMovieClick = onMovieClickStable,
                onMovieContextMenu = onMovieContextMenuStable,
                onProgressClick = onProgressClickStable,
                onProgressContextMenu = onProgressContextMenuStable,
                chunkedCategoryItems = chunkedCategoryItems,
                chunkedSearchResults = chunkedSearchResults,
                moviesFeaturedItems = state.moviesFeaturedItems,
                seriesFeaturedItems = state.seriesFeaturedItems,
                featuredItems = state.featuredItems,
                progressItems = state.progressItems,
                homeMovies = state.homeMovies,
                isMoviesLoading = state.isMoviesLoading,
                isSeriesLoading = state.isSeriesLoading,
                isKidsLoading = state.isKidsLoading,
                moviesFilterState = state.moviesFilterState,
                seriesFilterState = state.seriesFilterState,
                moviesFilters = state.moviesFilters,
                seriesFilters = state.seriesFilters,
            )
        }
    }
}

@Composable
private fun HomeMainContent(
    isSearchVisible: Boolean,
    selectedTabIndex: Int,
    searchQuery: String,
    favorites: List<Movie>,
    onEvent: (HomeEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    drawerFocusRequester: FocusRequester,
    filterFocusRequester: FocusRequester,
    contextMenuData: ContextMenuData?,
    onContextMenuChange: (ContextMenuData?) -> Unit,
    isFiltersVisible: Boolean,
    onFiltersVisibleChange: (Boolean) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
    onProgressClick: (ProgressItem) -> Unit,
    onProgressContextMenu: (ProgressItem) -> Unit,
    chunkedCategoryItems: List<List<Movie>>,
    chunkedSearchResults: List<List<Movie>>?,
    moviesFeaturedItems: List<FeaturedItem>,
    seriesFeaturedItems: List<FeaturedItem>,
    featuredItems: List<FeaturedItem>,
    progressItems: List<ProgressItem>,
    homeMovies: List<Movie>,
    isMoviesLoading: Boolean,
    isSeriesLoading: Boolean,
    isKidsLoading: Boolean,
    moviesFilterState: FilterState,
    seriesFilterState: FilterState,
    moviesFilters: FilterData?,
    seriesFilters: FilterData?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 72.dp)
            .focusRequester(contentFocusRequester)
            .focusProperties {
                left = drawerFocusRequester
                if (!isSearchVisible && selectedTabIndex != 0) {
                    right = filterFocusRequester
                }
            }
            .focusGroup()
            .focusRestorer(),
    ) {
        val firstItemFocusRequester = remember { FocusRequester() }
        var initialFocusRequested by remember(selectedTabIndex) { mutableStateOf(false) }

        LaunchedEffect(selectedTabIndex, chunkedCategoryItems.isNotEmpty()) {
            if (!initialFocusRequested && chunkedCategoryItems.isNotEmpty()) {
                runCatching { firstItemFocusRequester.requestFocus() }
                initialFocusRequested = true
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    canFocus = !isFiltersVisible && contextMenuData == null
                },
            contentPadding = PaddingValues(
                bottom = MaterialTheme.spacing.extraLarge,
            ),
        ) {
            homeScrollableContent(
                isSearchVisible = isSearchVisible,
                selectedTabIndex = selectedTabIndex,
                searchQuery = searchQuery,
                onEvent = onEvent,
                onMovieClick = onMovieClick,
                onMovieContextMenu = onMovieContextMenu,
                onProgressClick = onProgressClick,
                onProgressContextMenu = onProgressContextMenu,
                firstItemFocusRequester = firstItemFocusRequester,
                chunkedCategoryItems = chunkedCategoryItems,
                chunkedSearchResults = chunkedSearchResults,
                moviesFeaturedItems = moviesFeaturedItems,
                seriesFeaturedItems = seriesFeaturedItems,
                featuredItems = featuredItems,
                progressItems = progressItems,
                homeMovies = homeMovies,
                isMoviesLoading = isMoviesLoading,
                isSeriesLoading = isSeriesLoading,
                isKidsLoading = isKidsLoading,
                favorites = favorites,
            )
        }

        if (!isSearchVisible && selectedTabIndex != 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.extraLarge),
                contentAlignment = Alignment.TopEnd,
            ) {
                FilmanButton(
                    onClick = { onFiltersVisibleChange(true) },
                    modifier = Modifier.focusRequester(filterFocusRequester),
                    style = ButtonStyle.Secondary,
                ) {
                    Text(stringResource(R.string.home_filters))
                }
            }
        }

        HomeOverlays(
            onEvent = onEvent,
            isFiltersVisible = isFiltersVisible,
            onFiltersVisibleChange = onFiltersVisibleChange,
            contextMenuData = contextMenuData,
            onContextMenuChange = onContextMenuChange,
            favorites = favorites,
            selectedTabIndex = selectedTabIndex,
            moviesFilterState = moviesFilterState,
            seriesFilterState = seriesFilterState,
            moviesFilters = moviesFilters,
            seriesFilters = seriesFilters,
        )
    }
}

@Composable
private fun BoxScope.HomeOverlays(
    onEvent: (HomeEvent) -> Unit,
    isFiltersVisible: Boolean,
    onFiltersVisibleChange: (Boolean) -> Unit,
    contextMenuData: ContextMenuData?,
    onContextMenuChange: (ContextMenuData?) -> Unit,
    favorites: List<Movie>,
    selectedTabIndex: Int,
    moviesFilterState: FilterState,
    seriesFilterState: FilterState,
    moviesFilters: FilterData?,
    seriesFilters: FilterData?,
) {
    if (isFiltersVisible) {
        DialogOverlayTemplate {
            FiltersOverlay(
                selectedTabIndex = selectedTabIndex,
                moviesFilterState = moviesFilterState,
                seriesFilterState = seriesFilterState,
                moviesFilters = moviesFilters,
                seriesFilters = seriesFilters,
                onEvent = onEvent,
                onClose = { onFiltersVisibleChange(false) },
            )
        }
    } else if (contextMenuData != null) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(contextMenuData) {
            runCatching { focusRequester.requestFocus() }
        }

        DialogOverlayTemplate {
            HomeContextMenu(
                data = contextMenuData,
                favorites = favorites,
                onEvent = onEvent,
                onDismiss = { onContextMenuChange(null) },
                focusRequester = focusRequester,
            )
        }
    }
}

private fun LazyListScope.homeScrollableContent(
    isSearchVisible: Boolean,
    selectedTabIndex: Int,
    searchQuery: String,
    onEvent: (HomeEvent) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
    onProgressClick: (ProgressItem) -> Unit,
    onProgressContextMenu: (ProgressItem) -> Unit,
    firstItemFocusRequester: FocusRequester,
    chunkedCategoryItems: List<List<Movie>>,
    chunkedSearchResults: List<List<Movie>>?,
    moviesFeaturedItems: List<FeaturedItem>,
    seriesFeaturedItems: List<FeaturedItem>,
    featuredItems: List<FeaturedItem>,
    progressItems: List<ProgressItem>,
    homeMovies: List<Movie>,
    isMoviesLoading: Boolean,
    isSeriesLoading: Boolean,
    isKidsLoading: Boolean,
    favorites: List<Movie>,
) {
    if (isSearchVisible) {
        item(key = "search_bar") {
            SearchBar(
                searchQuery = searchQuery,
                onEvent = onEvent,
                modifier = Modifier
                    .animateItem()
                    .padding(top = MaterialTheme.spacing.extraLarge)
                    .padding(horizontal = MaterialTheme.spacing.extraLarge)
                    .padding(bottom = MaterialTheme.spacing.extraLarge),
            )
        }
        if (chunkedSearchResults != null) {
            searchResultsContent(
                chunkedResults = chunkedSearchResults,
                onMovieClick = onMovieClick,
                onMovieContextMenu = onMovieContextMenu,
            )
        }
    } else {
        if (selectedTabIndex == 0) {
            homeTabContent(
                featuredItems = featuredItems,
                progressItems = progressItems,
                favorites = favorites,
                homeMovies = homeMovies,
                onEvent = onEvent,
                onMovieClick = onMovieClick,
                onMovieContextMenu = onMovieContextMenu,
                onProgressClick = onProgressClick,
                onProgressContextMenu = onProgressContextMenu,
            )
        } else {
            categoryTabContent(
                selectedTabIndex = selectedTabIndex,
                isMoviesLoading = isMoviesLoading,
                isSeriesLoading = isSeriesLoading,
                isKidsLoading = isKidsLoading,
                moviesFeaturedItems = moviesFeaturedItems,
                seriesFeaturedItems = seriesFeaturedItems,
                onEvent = onEvent,
                chunkedItems = chunkedCategoryItems,
                onMovieClick = onMovieClick,
                onMovieContextMenu = onMovieContextMenu,
                firstItemFocusRequester = firstItemFocusRequester,
            )
        }
    }
}
