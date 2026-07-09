package com.example.filman.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.Episode
import com.example.filman.data.model.MediaDetails
import com.example.filman.ui.components.molecules.EpisodeCard
import com.example.filman.ui.core.CollectEffect
import com.example.filman.ui.core.suppressKeyRepeat
import com.example.filman.ui.theme.spacing

@Composable
fun MovieDetailsRoute(
    movieUrl: String,
    viewModel: MovieDetailsViewModel,
    onPlayMovie: (String) -> Unit,
    onAuthInvalid: () -> Unit,
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
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieDetailsScreen(
    state: MovieDetailsState,
    onEvent: (MovieDetailsEvent) -> Unit,
) {
    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
            )
        }

        return
    }

    val details = state.mediaDetails ?: return
    val seriesDetails = state.seriesDetails

    // UI specific state
    var initialFocusSet by remember { mutableStateOf(false) }
    val episodesLazyRowState = rememberLazyListState()
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }
    val onEpisodeClickStable =
        remember(onEvent) { { ep: Episode -> onEvent(MovieDetailsEvent.PlayEpisode(ep)) } }

    LaunchedEffect(details, seriesDetails) {
        if (details is MediaDetails.MovieOrEpisode && seriesDetails == null) {
            runCatching {
                playButtonFocusRequester.requestFocus()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image with Gradient Overlay
        AsyncImage(
            model = details.posterUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.background,
                        ),
                        startY = 0f,
                        endY = 1000f,
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = MaterialTheme.spacing.extraLarge),
        ) {
            // Top Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 70.dp, top = 50.dp, end = 70.dp, bottom = 40.dp),
                ) {
                    // Left Poster
                    AsyncImage(
                        model = details.posterUrl,
                        contentDescription = details.title,
                        modifier = Modifier
                            .width(250.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )

                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.extraExtraLarge))

                    // Right Info
                    Column {
                        Text(
                            modifier = Modifier.focusable(),
                            text = details.title,
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = MaterialTheme.spacing.medium),
                        ) {
                            Text(
                                text = stringResource(R.string.details_hd),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }

                        Text(
                            text = details.description,
                            modifier = Modifier.padding(top = MaterialTheme.spacing.large),
                            color = Color.White,
                            maxLines = 10,
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        // Action Buttons
                        Row(modifier = Modifier.padding(top = MaterialTheme.spacing.extraLarge)) {
                            if (details is MediaDetails.MovieOrEpisode && seriesDetails == null) {
                                // If it's a standalone movie, pass the movie URL to the player
                                Button(
                                    onClick = {
                                        onEvent(MovieDetailsEvent.PlayMovie(state.movieUrl))
                                    },
                                    modifier = Modifier
                                        .suppressKeyRepeat()
                                        .focusRequester(playButtonFocusRequester),
                                    colors = ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.8f,
                                        ),
                                        focusedContentColor = Color.White,
                                    ),
                                    shape = ButtonDefaults.shape(
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                                ) {
                                    Text(
                                        stringResource(R.string.details_watch_now),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                            }

                            OutlinedButton(
                                onClick = {
                                    onEvent(MovieDetailsEvent.ToggleFavorite)
                                },
                                modifier = Modifier.suppressKeyRepeat(),
                                colors = OutlinedButtonDefaults.colors(
                                    contentColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContentColor = Color.White,
                                ),
                                border = Border(
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.4f,
                                        ),
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                ).let { border ->
                                    OutlinedButtonDefaults.border(
                                        border = border,
                                        focusedBorder = border,
                                        disabledBorder = border,
                                        focusedDisabledBorder = border,
                                        pressedBorder = border,
                                    )
                                },
                                shape = OutlinedButtonDefaults.shape(
                                    shape = RoundedCornerShape(8.dp),
                                ),
                            ) {
                                Text(
                                    text = if (state.isFavorite) {
                                        stringResource(R.string.details_remove_favorite)
                                    } else {
                                        stringResource(R.string.details_add_favorite)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }

            // Seasons Row (if Series)
            if (seriesDetails != null) {
                item {
                    Row(
                        modifier = Modifier.padding(
                            start = 70.dp,
                            bottom = MaterialTheme.spacing.medium,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.details_seasons) + ":",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))

                        var expanded by remember { mutableStateOf(false) }
                        BackHandler(expanded) { expanded = false }
                        Box {
                            Surface(
                                onClick = { expanded = true },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.8f,
                                    ),
                                ),
                            ) {
                                Text(
                                    text = state.selectedSeason?.name
                                        ?: stringResource(R.string.details_select_season),
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.large,
                                        vertical = MaterialTheme.spacing.medium,
                                    ),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                seriesDetails.seasons.forEach { season ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(season.name, color = Color.White) },
                                        onClick = {
                                            onEvent(MovieDetailsEvent.SelectSeason(season))
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
                }

                // Episodes Row
                if (state.selectedSeason != null) {
                    item {
                        Text(
                            text = stringResource(R.string.details_episodes),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(
                                start = 70.dp,
                                bottom = MaterialTheme.spacing.medium,
                            ),
                            color = Color.White,
                        )
                        LaunchedEffect(state.selectedSeason) {
                            if (!initialFocusSet && state.nextEpisode != null) {
                                val index = state.nextEpisodeIndex
                                if (index >= 0) {
                                    episodesLazyRowState.scrollToItem(index)
                                    nextEpisodeFocusRequester.requestFocus()
                                    initialFocusSet = true
                                }
                            }
                        }

                        LazyRow(
                            state = episodesLazyRowState,
                            contentPadding = PaddingValues(start = 70.dp, end = 70.dp),
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                        ) {
                            items(state.selectedSeason.episodes, key = { it.url }) { episode ->
                                val progItem = state.progressMap[episode.url]
                                val prog = progItem?.progressPercentage ?: 0f
                                val isWatched = state.watchedSet.contains(episode.url)

                                val focusModifier =
                                    if (episode == state.nextEpisode && !initialFocusSet) {
                                        Modifier.focusRequester(nextEpisodeFocusRequester)
                                    } else {
                                        Modifier
                                    }

                                EpisodeCard(
                                    episode = episode,
                                    posterUrl = details.posterUrl,
                                    isWatched = isWatched,
                                    progressPercentage = prog,
                                    onClick = onEpisodeClickStable,
                                    modifier = focusModifier,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
                    }
                }
            }
        }
    }
}
