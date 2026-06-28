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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    BackHandler(state.searchResults != null) {
        viewModel.onEvent(HomeEvent.OnSearchQueryChanged(""))
        viewModel.onEvent(HomeEvent.OnSearchSubmit)
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

    val tabs = listOf(
        stringResource(R.string.home_tab_home),
        stringResource(R.string.home_movies),
        stringResource(R.string.home_series),
        stringResource(R.string.home_kids),
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = MaterialTheme.spacing.extraLarge,
            bottom = MaterialTheme.spacing.extraLarge,
            start = MaterialTheme.spacing.extraLarge,
            end = MaterialTheme.spacing.extraLarge,
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "top_bar") {
            TopBar(
                searchQuery = state.searchQuery,
                onEvent = onEvent,
            )
        }

        if (state.searchResults == null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "tabs") {
                TabRow(
                    selectedTabIndex = state.selectedTabIndex,
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.large),
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
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

        if (state.searchResults != null) {
            searchResultsContent(state.searchResults, onEvent)
        } else if (state.selectedTabIndex == 0) {
            homeTabContent(state, onEvent)
        } else {
            categoryTabContent(state, onEvent)
        }
    }
}

private fun LazyGridScope.searchResultsContent(
    searchResults: List<Movie>,
    onEvent: (HomeEvent) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "search_results_header") {
        Text(
            text = stringResource(R.string.home_search_results),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
    items(items = searchResults, key = { "search_results_${it.url}" }) { movie ->
        MovieCard(
            movie = movie,
            onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(150f / 220f),
        )
    }
}

private fun LazyGridScope.homeTabContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
) {
    if (state.progressItems.isNotEmpty()) {
        progressSection(state.progressItems, onEvent)
    }

    if (state.favorites.isNotEmpty()) {
        favoritesSection(state.favorites, onEvent)
    }

    recommendedSection(state.homeMovies, onEvent)
}

private fun LazyGridScope.progressSection(
    items: List<ProgressItem>,
    onEvent: (HomeEvent) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "continue_watching_header") {
        Text(
            text = stringResource(R.string.home_continue_watching),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
        )
    }
    item(span = { GridItemSpan(maxLineSpan) }, key = "continue_watching") {
        LazyRow(
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            items(items, key = { "continue_watching_${it.url}" }) { item ->
                ProgressCard(
                    item = item,
                    onClick = { onEvent(HomeEvent.OnMovieClick(item.url)) },
                    modifier = Modifier
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

private fun LazyGridScope.favoritesSection(
    items: List<Movie>,
    onEvent: (HomeEvent) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "favourites_header") {
        Text(
            text = stringResource(R.string.home_favorites),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium),
        )
    }
    item(span = { GridItemSpan(maxLineSpan) }, key = "favourites") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraLarge),
        ) {
            items(items, key = { "favourites_${it.url}" }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) },
                    modifier = Modifier
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

private fun LazyGridScope.recommendedSection(
    items: List<Movie>,
    onEvent: (HomeEvent) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "recommended_header") {
        Text(
            text = stringResource(R.string.home_recommended),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
    item(span = { GridItemSpan(maxLineSpan) }, key = "recommended") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            items(items, key = { "recommended_${it.url}" }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) },
                    modifier = Modifier
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

private fun LazyGridScope.categoryTabContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
) {
    val list = when (state.selectedTabIndex) {
        1 -> state.moviesList
        2 -> state.seriesList
        else -> state.kidsList
    }
    val isLoadingMore = when (state.selectedTabIndex) {
        1 -> state.isMoviesLoading
        2 -> state.isSeriesLoading
        else -> state.isKidsLoading
    }

    itemsIndexed(list, key = { _, it -> "movie_${it.url}" }) { index, item ->
        if (index == list.size - 1 && !isLoadingMore) {
            LaunchedEffect(index) {
                onEvent(HomeEvent.LoadNextPage(state.selectedTabIndex))
            }
        }

        MovieCard(
            movie = item,
            onClick = { onEvent(HomeEvent.OnMovieClick(item.url)) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(150f / 220f),
        )
    }

    if (isLoadingMore) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "loading_more") {
            Text(
                text = stringResource(R.string.loading_more),
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge),
            )
        }
    }
}

@Composable
fun TopBar(
    searchQuery: String,
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
        Text(
            text = stringResource(R.string.home_search),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = MaterialTheme.spacing.medium),
        )
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
                .background(Color.DarkGray)
                .padding(MaterialTheme.spacing.small),
        )
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

@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
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

            // Title overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(MaterialTheme.spacing.small),
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
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
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

            // Progress Bar and Title overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f)),
            ) {
                Text(
                    text = item.title,
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
