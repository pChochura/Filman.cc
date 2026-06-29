package com.example.filman.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
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
            contentAlignment = androidx.compose.ui.Alignment.Center,
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
    val firstTabFocusRequester = remember { FocusRequester() }

    val tabs = listOf(
        stringResource(R.string.home_tab_home),
        stringResource(R.string.home_movies),
        stringResource(R.string.home_series),
        stringResource(R.string.home_kids),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = MaterialTheme.spacing.extraLarge),
        ) {
            item(key = "top_bar") {
                TopBar(
                    searchQuery = state.searchQuery,
                    isSearchVisible = state.isSearchVisible,
                    onEvent = onEvent,
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = MaterialTheme.spacing.extraLarge),
                )
            }

            if (state.searchResults == null) {
                item(key = "tabs") {
                    TabRow(
                        selectedTabIndex = state.selectedTabIndex,
                        modifier = Modifier
                            .animateItem()
                            .focusRestorer(firstTabFocusRequester)
                            .padding(horizontal = MaterialTheme.spacing.extraLarge)
                            .padding(bottom = MaterialTheme.spacing.large),
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                modifier = if (index == 0) {
                                    Modifier.focusRequester(firstTabFocusRequester)
                                } else {
                                    Modifier
                                },
                                selected = state.selectedTabIndex == index,
                                onFocus = { onEvent(HomeEvent.OnTabSelected(index)) },
                            ) {
                                Text(
                                    text = title,
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.medium,
                                        vertical = MaterialTheme.spacing.small,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            val onContextMenu = { data: ContextMenuData -> contextMenuData = data }

            if (state.searchResults != null) {
                searchResultsContent(state.searchResults, onEvent, onContextMenu)
            } else if (state.selectedTabIndex == 0) {
                homeTabContent(state, onEvent, onContextMenu)
            } else {
                categoryTabContent(state, onEvent, onContextMenu)
            }
        }

        if (contextMenuData != null) {
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
                    .align(androidx.compose.ui.Alignment.CenterEnd)
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
    items(chunkedResults.size) { rowIndex ->
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

@Composable
private fun MovieGridRow(
    movies: List<Movie>,
    onMovieClick: (String) -> Unit,
    onContextMenu: (ContextMenuData) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        for (movie in movies) {
            MovieCard(
                movie = movie,
                onClick = { onMovieClick(movie.url) },
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
                    .weight(1f)
                    .aspectRatio(150f / 220f),
            )
        }
        // Fill remaining space if last row is incomplete
        repeat(5 - movies.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun LazyListScope.homeTabContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onContextMenu: (ContextMenuData) -> Unit,
) {
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

    val chunkedItems = items.chunked(5)
    items(chunkedItems.size) { rowIndex ->
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

@Composable
fun TopBar(
    searchQuery: String,
    isSearchVisible: Boolean,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.spacing.extraLarge),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current

        if (!isSearchVisible) {
            IconButton(
                onClick = { onEvent(HomeEvent.OnSearchVisibleChanged(true)) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { onEvent(HomeEvent.OnLogoutClick) }) {
                Text(stringResource(R.string.home_logout))
            }
        } else {
            val focusRequester = remember { FocusRequester() }

            BasicTextField(
                value = searchQuery,
                onValueChange = { onEvent(HomeEvent.OnSearchQueryChanged(it)) },
                textStyle = TextStyle(color = Color.White),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        onEvent(HomeEvent.OnSearchSubmit)
                    },
                ),
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.DarkGray)
                    .padding(
                        horizontal = MaterialTheme.spacing.medium,
                        vertical = MaterialTheme.spacing.small,
                    )
                    .focusRequester(focusRequester),
            )

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
            Button(
                onClick = {
                    keyboardController?.hide()
                    onEvent(HomeEvent.OnSearchSubmit)
                },
            ) {
                Text(stringResource(R.string.home_go))
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(onClick = { onEvent(HomeEvent.OnLogoutClick) }) {
                Text(stringResource(R.string.home_logout))
            }
        }
    }
}

@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (movie.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = movie.posterUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
fun ProgressCard(
    item: ProgressItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val seasonEpisodeRegex1 =
        remember { Regex("(?i)(?:sezon|season)\\s*(\\d+)[\\s-]*(?:odcinek|episode)\\s*(\\d+)") }
    val seasonEpisodeRegex2 = remember { Regex("(?i)s(\\d+)e(\\d+)") }

    var badgeText: String? = null
    var displayTitle = item.title

    val match1 = seasonEpisodeRegex1.find(item.title)
    if (match1 != null) {
        badgeText = "S${match1.groupValues[1]} E${match1.groupValues[2]}"
        val baseTitle = item.title.substring(0, match1.range.first).trim(' ', '-')
        displayTitle = if (!item.seriesTitle.isNullOrBlank()) item.seriesTitle else baseTitle
    } else {
        val match2 = seasonEpisodeRegex2.find(item.title)
        if (match2 != null) {
            badgeText = "S${match2.groupValues[1]} E${match2.groupValues[2]}"
            val baseTitle = item.title.substring(0, match2.range.first).trim(' ', '-')
            displayTitle = if (!item.seriesTitle.isNullOrBlank()) item.seriesTitle else baseTitle
        } else if (!item.seriesTitle.isNullOrBlank()) {
            val matchUrl = seasonEpisodeRegex2.find(item.url)
            if (matchUrl != null) {
                badgeText = "S${matchUrl.groupValues[1]} E${matchUrl.groupValues[2]}"
            }
            displayTitle = item.seriesTitle
        }
    }

    if (displayTitle.isBlank()) {
        displayTitle = item.title
    }

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                )
            }

            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.TopEnd)
                        .padding(MaterialTheme.spacing.small)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }

            // Progress Bar and Title overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    modifier = Modifier.padding(MaterialTheme.spacing.small),
                )

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.DarkGray),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progressPercentage)
                            .background(Color.Red),
                    )
                }
            }
        }
    }
}

@Immutable
private data class ContextMenuData(
    val url: String,
    val title: String,
    val posterUrl: String,
    val isProgress: Boolean,
    val seriesUrl: String? = null,
)
