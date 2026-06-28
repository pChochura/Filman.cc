package com.example.filman.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.example.filman.data.model.MediaDetails
import com.example.filman.data.local.ProgressManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.filman.ui.core.CollectEffect

@Composable
fun MovieDetailsRoute(
    movieUrl: String,
    viewModel: MovieDetailsViewModel,
    progressManager: ProgressManager,
    onPlayMovie: (String) -> Unit,
    onAuthInvalid: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(movieUrl) {
        viewModel.onEvent(MovieDetailsEvent.LoadDetails(movieUrl))
    }

    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            MovieDetailsEffect.NavigateToAuth -> onAuthInvalid()
            is MovieDetailsEffect.NavigateToPlayer -> onPlayMovie(effect.url)
        }
    }

    MovieDetailsScreen(
        state = state,
        progressManager = progressManager,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    state: MovieDetailsState,
    progressManager: ProgressManager,
    onEvent: (MovieDetailsEvent) -> Unit
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...", modifier = Modifier.fillMaxSize(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }

    val details = state.mediaDetails ?: return
    val seriesDetails = state.seriesDetails

    // UI specific state
    var initialFocusSet by remember { mutableStateOf(false) }
    val episodesLazyRowState = rememberLazyListState()
    val nextEpisodeFocusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image with Gradient Overlay
        AsyncImage(
            model = details.posterUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black,
                            Color.Black
                        ),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Top Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 70.dp, top = 50.dp, end = 70.dp, bottom = 40.dp)
                ) {
                    // Left Poster
                    AsyncImage(
                        model = details.posterUrl,
                        contentDescription = details.title,
                        modifier = Modifier
                            .width(250.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(40.dp))

                    // Right Info
                    Column {
                        Text(
                            text = details.title,
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(text = "HD", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Text(
                            text = details.description,
                            modifier = Modifier.padding(top = 24.dp),
                            color = Color.White,
                            maxLines = 10,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // Action Buttons
                        Row(modifier = Modifier.padding(top = 32.dp)) {
                            if (details is MediaDetails.MovieOrEpisode && seriesDetails == null) {
                                // If it's a standalone movie, pass the movie URL to the player
                                Button(onClick = {
                                    onEvent(MovieDetailsEvent.PlayMovie(state.movieUrl))
                                }) {
                                    Text("Watch Now")
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            Button(onClick = {
                                onEvent(MovieDetailsEvent.ToggleFavorite)
                            }) {
                                Text(if (state.isFavorite) "Remove from Favorites" else "Add to Favorites")
                            }
                        }
                    }
                }
            }

            // Seasons Row (if Series)
            if (seriesDetails != null) {
                item {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 70.dp, bottom = 16.dp),
                        color = Color.White
                    )
                    LazyRow(
                        contentPadding = PaddingValues(start = 70.dp, end = 70.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(seriesDetails.seasons) { season ->
                            Surface(
                                onClick = { onEvent(MovieDetailsEvent.SelectSeason(season)) },
                                modifier = Modifier
                                    .width(140.dp)
                                    .aspectRatio(2f / 3f)
                                    .onFocusChanged { fState ->
                                        if (fState.isFocused) {
                                            onEvent(MovieDetailsEvent.SelectSeason(season))
                                        }
                                    },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = details.posterUrl, // fallback to series poster
                                        contentDescription = season.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    )
                                    Text(
                                        text = season.name,
                                        modifier = Modifier.align(Alignment.Center),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // Episodes Row
                if (state.selectedSeason != null) {
                    item {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 70.dp, bottom = 16.dp),
                            color = Color.White
                        )
                        LaunchedEffect(state.selectedSeason) {
                            if (!initialFocusSet && state.nextEpisode != null) {
                                val index = state.nextEpisodeIndex
                                if (index >= 0) {
                                    episodesLazyRowState.scrollToItem(index)
                                    try {
                                        nextEpisodeFocusRequester.requestFocus()
                                    } catch (e: Exception) { }
                                    initialFocusSet = true
                                }
                            }
                        }

                        LazyRow(
                            state = episodesLazyRowState,
                            contentPadding = PaddingValues(start = 70.dp, end = 70.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(state.selectedSeason.episodes) { episode ->
                                val progItem = progressManager.getProgressForUrl(episode.url)
                                val prog = progItem?.progressPercentage ?: 0f
                                val isWatched = prog >= 0.95f

                                val focusModifier = if (episode == state.nextEpisode && !initialFocusSet) {
                                    Modifier.focusRequester(nextEpisodeFocusRequester)
                                } else {
                                    Modifier
                                }

                                Surface(
                                    onClick = {
                                        onEvent(MovieDetailsEvent.PlayEpisode(episode))
                                    },
                                    modifier = focusModifier
                                        .width(240.dp)
                                        .aspectRatio(2f / 1f),
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = details.posterUrl, // fallback to series poster
                                            contentDescription = episode.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            alpha = if (isWatched) 0.5f else 1f
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = if (isWatched) 0.7f else 0.4f))
                                        )
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            if (isWatched) {
                                                Text("Watched", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text(
                                                text = episode.title,
                                                color = if (isWatched) Color.LightGray else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            if (prog > 0f && !isWatched) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .background(Color.DarkGray, RoundedCornerShape(2.dp))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(prog)
                                                            .fillMaxHeight()
                                                            .background(Color.Red, RoundedCornerShape(2.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
