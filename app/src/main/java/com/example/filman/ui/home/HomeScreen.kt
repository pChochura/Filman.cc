package com.example.filman.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import com.example.filman.ui.components.molecules.MovieCard
import com.example.filman.ui.components.molecules.ProgressCard
import com.example.filman.ui.components.molecules.SearchBar
import com.example.filman.ui.components.organisms.FeaturedSection
import com.example.filman.ui.components.organisms.FiltersOverlay
import com.example.filman.ui.components.organisms.MovieGridRow
import com.example.filman.ui.home.ContextMenuData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import coil.compose.AsyncImage
import com.example.filman.ui.core.suppressKeyRepeat
import com.example.filman.R
import com.example.filman.data.model.FeaturedItem
import com.example.filman.data.model.Movie
import com.example.filman.data.model.ProgressItem
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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
    if (state.error != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
                )
                Button(onClick = { onEvent(HomeEvent.LoadHomeData) }) {
                    Text(stringResource(R.string.filters_apply))
                }
            }
        }
        return
    }

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
            )
        }
        return
    }

    var contextMenuData by remember { mutableStateOf<ContextMenuData?>(null) }
    var isFiltersVisible by remember { mutableStateOf(false) }

    val onMovieClickStable = remember(onEvent) { { movie: Movie -> onEvent(HomeEvent.OnMovieClick(movie.url)) } }
    val onMovieContextMenuStable = remember {
        { movie: Movie ->
            contextMenuData = ContextMenuData(
                url = movie.url,
                title = movie.title,
                posterUrl = movie.posterUrl,
                isProgress = false,
            )
        }
    }
    val onProgressClickStable = remember(onEvent) { { item: ProgressItem -> onEvent(HomeEvent.OnMovieClick(item.url)) } }
    val onProgressContextMenuStable = remember {
        { item: ProgressItem ->
            contextMenuData = ContextMenuData(
                url = item.url,
                title = item.title,
                posterUrl = item.posterUrl,
                isProgress = true,
                seriesUrl = item.seriesUrl,
            )
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    // The TV NavigationDrawer is focus-driven: the drawer opens when its content gains
    // focus and closes when focus moves back to the main content.
    //
    // isDrawerOpen tracks whether the drawer is currently open so BackHandlers can
    // react correctly. derivedStateOf ensures recomposition when the value changes.
    val isDrawerOpen by remember { derivedStateOf { drawerState.currentValue == DrawerValue.Open } }
    val nothingOpen by remember {
        derivedStateOf {
            !isFiltersVisible && contextMenuData == null && !state.isSearchVisible
        }
    }

    // When the drawer is open and Back is pressed, move focus back to main content
    // (this closes the drawer) and consume the event so NavDisplay doesn't also pop.
    BackHandler(enabled = isDrawerOpen) {
        contentFocusRequester.requestFocus()
    }

    // When nothing is open and Back is pressed from the main content, open the drawer
    // by requesting focus on it. Consume the event so NavDisplay doesn't pop Home.
    BackHandler(enabled = !isDrawerOpen && nothingOpen) {
        drawerFocusRequester.requestFocus()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1000f
                        )
                    )
                    .padding(MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.Center,
            ) {
                // Search button — first item, receives focus to open the drawer
                NavigationDrawerItem(
                    selected = state.isSearchVisible,
                    onClick = { onEvent(HomeEvent.OnSearchVisibleChanged(!state.isSearchVisible)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.home_search_drawer),
                        )
                    },
                    modifier = Modifier.focusRequester(drawerFocusRequester),
                ) {
                    Text(stringResource(R.string.home_search_drawer))
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

                // Tabs
                val tabIcons = listOf(
                    R.drawable.ic_home to stringResource(R.string.home_tab_home),
                    R.drawable.ic_movie to stringResource(R.string.home_movies),
                    R.drawable.ic_series to stringResource(R.string.home_series),
                    R.drawable.ic_kids to stringResource(R.string.home_kids),
                )

                tabIcons.forEachIndexed { index, (icon, title) ->
                    NavigationDrawerItem(
                        selected = state.selectedTabIndex == index,
                        onClick = { onEvent(HomeEvent.OnTabSelected(index)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = title,
                            )
                        },
                    ) {
                        Text(title)
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.small))
                }

                Spacer(modifier = Modifier.weight(1f))

                // Logout button
                NavigationDrawerItem(
                    selected = false,
                    onClick = { onEvent(HomeEvent.OnLogoutClick) },
                    leadingContent = {
                        Icon(imageVector = Icons.Rounded.ExitToApp, contentDescription = "Logout")
                    },
                ) {
                    Text(stringResource(R.string.home_logout))
                }
            }
        },
    ) {
        val filterFocusRequester = remember { FocusRequester() }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 72.dp)
                .focusRequester(contentFocusRequester)
                .focusProperties {
                    if (!state.isSearchVisible && state.selectedTabIndex != 0) {
                        right = filterFocusRequester
                    }
                }
                .focusGroup()
                .focusRestorer(),
        ) {
            val firstItemFocusRequester = remember { FocusRequester() }
            var initialFocusRequested by remember(state.selectedTabIndex) { mutableStateOf(false) }

            val featuredItems = when (state.selectedTabIndex) {
                1 -> state.moviesFeaturedItems
                2 -> state.seriesFeaturedItems
                else -> emptyList()
            }
            val items = when (state.selectedTabIndex) {
                1 -> state.moviesList
                2 -> state.seriesList
                3 -> state.kidsList
                else -> emptyList()
            }

            val chunkedCategoryItems = remember(items) { items.chunked(5) }
            val chunkedSearchResults = remember(state.searchResults) { state.searchResults?.chunked(5) }

            LaunchedEffect(state.selectedTabIndex, items.isNotEmpty(), featuredItems.isEmpty()) {
                if (!initialFocusRequested && items.isNotEmpty() && featuredItems.isEmpty()) {
                    runCatching { firstItemFocusRequester.requestFocus() }
                    initialFocusRequested = true
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = MaterialTheme.spacing.extraLarge,
                ),
            ) {
                if (state.isSearchVisible) {
                    item(key = "search_bar") {
                        SearchBar(
                            searchQuery = state.searchQuery,
                            onEvent = onEvent,
                            modifier = Modifier
                                .animateItem()
                                .padding(top = MaterialTheme.spacing.extraLarge)
                                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                                .padding(bottom = MaterialTheme.spacing.extraLarge),
                        )
                    }
                    if (chunkedSearchResults != null) {
                        searchResultsContent(chunkedSearchResults, onMovieClickStable, onMovieContextMenuStable)
                    }
                } else {
                    if (state.selectedTabIndex == 0) {
                        homeTabContent(
                            state = state,
                            onEvent = onEvent,
                            onMovieClick = onMovieClickStable,
                            onMovieContextMenu = onMovieContextMenuStable,
                            onProgressClick = onProgressClickStable,
                            onProgressContextMenu = onProgressContextMenuStable,
                        )
                    } else {
                        categoryTabContent(
                            state = state,
                            onEvent = onEvent,
                            chunkedItems = chunkedCategoryItems,
                            onMovieClick = onMovieClickStable,
                            onMovieContextMenu = onMovieContextMenuStable,
                            firstItemFocusRequester = firstItemFocusRequester,
                        ) {
                            isFiltersVisible = true
                        }
                    }
                }
            }

            if (!state.isSearchVisible && state.selectedTabIndex != 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.extraLarge),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Button(
                        onClick = { isFiltersVisible = true },
                        modifier = Modifier.focusRequester(filterFocusRequester)
                    ) {
                        Text(stringResource(R.string.home_filters))
                    }
                }
            }

            if (isFiltersVisible) {
                BackHandler(isFiltersVisible) {
                    isFiltersVisible = false
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(400.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .align(Alignment.CenterEnd)
                        .padding(MaterialTheme.spacing.extraLarge),
                ) {
                    FiltersOverlay(
                        state = state,
                        onEvent = onEvent,
                        onClose = { isFiltersVisible = false }
                    )
                }
            } else if (contextMenuData != null) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(contextMenuData) {
                    runCatching { focusRequester.requestFocus() }
                }
                BackHandler(contextMenuData != null) {
                    contextMenuData = null
                }
                var targetUrl = if (contextMenuData!!.seriesUrl != null) {
                    contextMenuData!!.seriesUrl!!
                } else if (contextMenuData!!.isProgress) {
                    contextMenuData!!.url.replace(Regex("(?i)/s\\d+(?:e\\d+)?/?$"), "")
                } else {
                    contextMenuData!!.url
                }
                targetUrl = targetUrl.replace(Regex("^https?://[^/]+"), "")

                val targetTitle =
                    if (contextMenuData!!.isProgress && contextMenuData!!.title.contains(" - ")) {
                        contextMenuData!!.title.substringBefore(" - ").trim()
                    } else {
                        contextMenuData!!.title
                    }

                val isFavorite = state.favorites.any { it.url == targetUrl }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(400.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .align(Alignment.CenterEnd)
                        .padding(MaterialTheme.spacing.extraLarge),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                        Text(
                            text = contextMenuData!!.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = MaterialTheme.spacing.large),
                        )
                        if (contextMenuData!!.isProgress) {
                            Button(
                                onClick = {
                                    onEvent(HomeEvent.RemoveFromProgress(contextMenuData!!.url))
                                    contextMenuData = null
                                },
                                modifier = Modifier
                                    .suppressKeyRepeat()
                                    .focusRequester(focusRequester)
                                    .fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.remove_from_continue_watching))
                            }
                        }
                        Button(
                            onClick = {
                                if (isFavorite) {
                                    onEvent(HomeEvent.RemoveFromFavorites(targetUrl))
                                } else {
                                    onEvent(
                                        HomeEvent.AddToFavorites(
                                            Movie(
                                                url = targetUrl,
                                                title = targetTitle,
                                                posterUrl = contextMenuData!!.posterUrl,
                                            ),
                                        ),
                                    )
                                }
                                contextMenuData = null
                            },
                            modifier = Modifier
                                .suppressKeyRepeat()
                                .then(
                                    if (!contextMenuData!!.isProgress) {
                                        Modifier.focusRequester(focusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .fillMaxWidth(),
                        ) {
                            Text(
                                if (isFavorite) {
                                    stringResource(R.string.remove_from_favorites)
                                } else {
                                    stringResource(R.string.add_to_favorites)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.searchResultsContent(
    chunkedResults: List<List<Movie>>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
) {
    item(key = "search_results_header") {
        Text(
            text = stringResource(R.string.home_search_results),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .animateItem()
                .padding(horizontal = MaterialTheme.spacing.extraLarge),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
    }

    items(chunkedResults.size, key = { index -> "search_row_${chunkedResults[index].firstOrNull()?.url ?: index}" }) { rowIndex ->
        val rowItems = chunkedResults[rowIndex]
        MovieGridRow(
            movies = rowItems,
            onMovieClick = onMovieClick,
            onContextMenu = onMovieContextMenu,
            modifier = Modifier
                .animateItem()
                .padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.small,
                ),
        )
    }
}



private fun LazyListScope.homeTabContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
    onProgressClick: (ProgressItem) -> Unit,
    onProgressContextMenu: (ProgressItem) -> Unit,
) {

    if (state.featuredItems.isNotEmpty()) {
        item(key = "featured_section") {
            FeaturedSection(
                items = state.featuredItems,
                onEvent = onEvent,
                modifier = Modifier
                    .animateItem()
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.85f),
            )
        }
    }

    if (state.progressItems.isNotEmpty()) {
        progressSection(state.progressItems, onProgressClick, onProgressContextMenu)
    }

    if (state.favorites.isNotEmpty()) {
        favoritesSection(state.favorites, onMovieClick, onMovieContextMenu)
    }

    recommendedSection(state.homeMovies, onMovieClick, onMovieContextMenu)
}

private fun LazyListScope.progressSection(
    items: List<ProgressItem>,
    onProgressClick: (ProgressItem) -> Unit,
    onProgressContextMenu: (ProgressItem) -> Unit,
) {
    item(key = "continue_watching_header") {
        Text(
            text = stringResource(R.string.home_continue_watching),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .animateItem()
                .padding(top = MaterialTheme.spacing.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                .padding(bottom = MaterialTheme.spacing.medium),
        )
    }

    item(key = "continue_watching") {
        val firstItemFocusRequester = remember { FocusRequester() }

        LazyRow(
            modifier = Modifier
                .animateItem()
                .focusRestorer(firstItemFocusRequester)
                .padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
        ) {
            itemsIndexed(items, key = { _, it -> "continue_watching_${it.url}" }) { index, item ->
                ProgressCard(
                    item = item,
                    onClick = onProgressClick,
                    onLongClick = onProgressContextMenu,
                    modifier = Modifier
                        .then(
                            if (index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .animateItem()
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

private fun LazyListScope.favoritesSection(
    items: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
) {
    item(key = "favourites_header") {
        Text(
            text = stringResource(R.string.home_favorites),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .animateItem()
                .padding(top = MaterialTheme.spacing.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                .padding(bottom = MaterialTheme.spacing.medium),
        )
    }
    item(key = "favourites") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
            modifier = Modifier
                .animateItem()
                .focusRestorer()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        ) {
            items(items, key = { "favourites_${it.url}" }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = onMovieClick,
                    onLongClick = onMovieContextMenu,
                    modifier = Modifier
                        .animateItem()
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

private fun LazyListScope.recommendedSection(
    items: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
) {
    item(key = "recommended_header") {
        Text(
            text = stringResource(R.string.home_recommended),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .animateItem()
                .padding(top = MaterialTheme.spacing.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.extraLarge),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
    }
    item(key = "recommended") {
        LazyRow(
            modifier = Modifier
                .animateItem()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
        ) {
            items(items, key = { "recommended_${it.url}" }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = onMovieClick,
                    onLongClick = onMovieContextMenu,
                    modifier = Modifier
                        .animateItem()
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

private fun LazyListScope.categoryTabContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    chunkedItems: List<List<Movie>>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
    firstItemFocusRequester: FocusRequester,
    onFilterClick: () -> Unit,
) {
    val isLoading = when (state.selectedTabIndex) {
        1 -> state.isMoviesLoading
        2 -> state.isSeriesLoading
        3 -> state.isKidsLoading
        else -> false
    }
    val featuredItems = when (state.selectedTabIndex) {
        1 -> state.moviesFeaturedItems
        2 -> state.seriesFeaturedItems
        else -> emptyList()
    }

    if (featuredItems.isNotEmpty()) {
        item(key = "featured_section_${state.selectedTabIndex}") {
            FeaturedSection(
                items = featuredItems,
                onEvent = onEvent,
                modifier = Modifier
                    .animateItem()
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.85f),
            )
        }
    }

    item(key = "top_padding") {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
    }

    items(chunkedItems.size, key = { index -> "category_row_${chunkedItems[index].firstOrNull()?.url ?: index}" }) { rowIndex ->
        if (rowIndex == chunkedItems.size - 1 && !isLoading) {
            LaunchedEffect(rowIndex) {
                onEvent(HomeEvent.LoadNextPage(state.selectedTabIndex))
            }
        }

        val rowItems = chunkedItems[rowIndex]
        MovieGridRow(
            movies = rowItems,
            onMovieClick = onMovieClick,
            onContextMenu = onMovieContextMenu,
            modifier = Modifier
                .animateItem()
                .padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.small,
                ),
            firstItemModifier = if (rowIndex == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
        )
    }

    if (isLoading) {
        item(key = "loading_more") {
            Text(
                text = stringResource(R.string.loading_more),
                modifier = Modifier
                    .animateItem()
                    .padding(MaterialTheme.spacing.extraLarge),
            )
        }
    }
}









