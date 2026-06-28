package com.example.filman.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import coil.compose.AsyncImage
import com.example.filman.data.model.Movie
import com.example.filman.data.model.ProgressItem
import com.example.filman.ui.core.CollectEffect

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

    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text("Loading...", modifier = Modifier.fillMaxSize(), textAlign = TextAlign.Center)
        }
        return
    }

    val tabs = listOf("Home", "Movies", "Series", "Kids")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp),
    ) {
        item {
            // Search Bar at Top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 32.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                val keyboardController = LocalSoftwareKeyboardController.current
                Text(
                    "Search:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 16.dp),
                )
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = { onEvent(HomeEvent.OnSearchQueryChanged(it)) },
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            onEvent(HomeEvent.OnSearchSubmit)
                        },
                    ),
                    modifier = Modifier
                        .width(300.dp)
                        .background(Color.DarkGray)
                        .padding(8.dp),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onEvent(HomeEvent.OnSearchSubmit)
                    },
                ) {
                    Text("Go")
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = { onEvent(HomeEvent.OnLogoutClick) }) {
                    Text("Logout")
                }
            }
        }

        item {
            TabRow(
                selectedTabIndex = state.selectedTabIndex,
                modifier = Modifier.padding(horizontal = 32.dp).padding(bottom = 24.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTabIndex == index,
                        onFocus = { onEvent(HomeEvent.OnTabSelected(index)) },
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (state.searchResults != null) {
            item {
                Text(
                    "Search Results",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        horizontal = 32.dp,
                    ),
                ) {
                    items(state.searchResults) { movie ->
                        MovieCard(movie = movie, onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) })
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        } else if (state.selectedTabIndex == 0) {
            if (state.progressItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Continue Watching",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 16.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                    ) {
                        items(state.progressItems) { item ->
                            ProgressCard(item = item, onClick = { onEvent(HomeEvent.OnMovieClick(item.url)) })
                        }
                    }
                }
            }

            if (state.favorites.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Favorites",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 16.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 32.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                    ) {
                        items(state.favorites) { movie ->
                            MovieCard(movie = movie, onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) })
                        }
                    }
                }
            }

            item {
                Text(
                    "Recommended",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                ) {
                    items(state.homeMovies) { movie ->
                        MovieCard(movie = movie, onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) })
                    }
                }
            }
        } else {
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

            val chunkedList = list.chunked(6)
            items(chunkedList.size) { rowIndex ->
                val rowMovies = chunkedList[rowIndex]

                if (rowIndex == chunkedList.size - 1 && !isLoadingMore) {
                    LaunchedEffect(rowIndex) {
                        onEvent(HomeEvent.LoadNextPage(state.selectedTabIndex))
                    }
                }

                Row(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (movie in rowMovies) {
                        MovieCard(movie = movie, onClick = { onEvent(HomeEvent.OnMovieClick(movie.url)) })
                    }
                }
            }

            if (isLoadingMore) {
                item {
                    Text("Loading more...", modifier = Modifier.padding(32.dp))
                }
            }
        }
    }
}

@Composable
fun MovieCard(movie: Movie, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .height(220.dp),
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
                    .padding(8.dp),
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
fun ProgressCard(item: ProgressItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(150.dp)
            .height(220.dp),
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
                    modifier = Modifier.padding(8.dp),
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
