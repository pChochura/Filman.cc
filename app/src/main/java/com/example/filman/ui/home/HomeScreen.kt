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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import coil.compose.AsyncImage
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
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.loading),
                modifier = Modifier.fillMaxSize(),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    var contextMenuData by remember { mutableStateOf<ContextMenuData?>(null) }
    var isFiltersVisible by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.Center,
            ) {
                // Search button
                NavigationDrawerItem(
                    selected = state.isSearchVisible,
                    onClick = { onEvent(HomeEvent.OnSearchVisibleChanged(!state.isSearchVisible)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.home_search_drawer),
                        )
                    },
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = MaterialTheme.spacing.extraLarge,
                ),
            ) {
                val onContextMenu = { data: ContextMenuData -> contextMenuData = data }

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
                    if (state.searchResults != null) {
                        searchResultsContent(state.searchResults, onEvent, onContextMenu)
                    }
                } else {
                    if (state.selectedTabIndex == 0) {
                        homeTabContent(state, onEvent, onContextMenu)
                    } else {
                        categoryTabContent(state, onEvent, onContextMenu) {
                            isFiltersVisible = true
                        }
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
                        .width(350.dp)
                        .background(Color.Black.copy(alpha = 0.95f))
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
                    delay(100.milliseconds)
                    focusRequester.requestFocus()
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
                        .width(350.dp)
                        .background(Color.Black.copy(alpha = 0.9f))
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
    searchResults: List<Movie>,
    onEvent: (HomeEvent) -> Unit,
    onContextMenu: (ContextMenuData) -> Unit,
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

    val chunkedResults = searchResults.chunked(5)
    items(chunkedResults.size, key = { index -> "search_row_${chunkedResults[index].firstOrNull()?.url ?: index}" }) { rowIndex ->
        val rowItems = chunkedResults[rowIndex]
        MovieGridRow(
            movies = rowItems,
            onMovieClick = { onEvent(HomeEvent.OnMovieClick(it)) },
            onContextMenu = onContextMenu,
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
    onContextMenu: (ContextMenuData) -> Unit,
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
        progressSection(state.progressItems, onEvent, onContextMenu)
    }

    if (state.favorites.isNotEmpty()) {
        favoritesSection(state.favorites, onEvent, onContextMenu)
    }

    recommendedSection(state.homeMovies, onEvent, onContextMenu)
}

private fun LazyListScope.progressSection(
    items: List<ProgressItem>,
    onEvent: (HomeEvent) -> Unit,
    onContextMenu: (ContextMenuData) -> Unit,
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
                    onClick = { onEvent(HomeEvent.OnMovieClick(item.url)) },
                    onLongClick = {
                        onContextMenu(
                            ContextMenuData(
                                url = item.url,
                                title = item.title,
                                posterUrl = item.posterUrl,
                                isProgress = true,
                                seriesUrl = item.seriesUrl,
                            ),
                        )
                    },
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
    onEvent: (HomeEvent) -> Unit,
    onContextMenu: (ContextMenuData) -> Unit,
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
                    onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) },
                    onLongClick = {
                        onContextMenu(
                            ContextMenuData(
                                url = movie.url,
                                title = movie.title,
                                posterUrl = movie.posterUrl,
                                isProgress = false,
                            ),
                        )
                    },
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
    onEvent: (HomeEvent) -> Unit,
    onContextMenu: (ContextMenuData) -> Unit,
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
                    onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) },
                    onLongClick = {
                        onContextMenu(
                            ContextMenuData(
                                url = movie.url,
                                title = movie.title,
                                posterUrl = movie.posterUrl,
                                isProgress = false,
                            ),
                        )
                    },
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
    onContextMenu: (ContextMenuData) -> Unit,
    onFilterClick: () -> Unit,
) {
    val items = when (state.selectedTabIndex) {
        1 -> state.moviesList
        2 -> state.seriesList
        3 -> state.kidsList
        else -> emptyList()
    }
    val isLoading = when (state.selectedTabIndex) {
        1 -> state.isMoviesLoading
        2 -> state.isSeriesLoading
        3 -> state.isKidsLoading
        else -> false
    }

    item {
        Row(
            modifier = Modifier
                .animateItem()
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.extraLarge,
                    end = MaterialTheme.spacing.extraLarge,
                    top = MaterialTheme.spacing.extraLarge,
                ),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onFilterClick) {
                Text(stringResource(R.string.home_filters))
            }
        }
    }

    val chunkedItems = items.chunked(5)
    items(chunkedItems.size, key = { index -> "category_row_${chunkedItems[index].firstOrNull()?.url ?: index}" }) { rowIndex ->
        if (rowIndex == chunkedItems.size - 1 && !isLoading) {
            LaunchedEffect(rowIndex) {
                onEvent(HomeEvent.LoadNextPage(state.selectedTabIndex))
            }
        }

        val rowItems = chunkedItems[rowIndex]
        MovieGridRow(
            movies = rowItems,
            onMovieClick = { onEvent(HomeEvent.OnMovieClick(it)) },
            onContextMenu = onContextMenu,
            modifier = Modifier
                .animateItem()
                .padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.small,
                ),
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









