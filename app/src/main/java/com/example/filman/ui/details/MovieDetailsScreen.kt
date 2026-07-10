package com.example.filman.ui.details

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.Episode
import com.example.filman.data.model.MediaDetails
import com.example.filman.data.model.ProgressItem
import com.example.filman.data.model.Season
import com.example.filman.ui.components.atoms.ButtonStyle
import com.example.filman.ui.components.atoms.FilmanButton
import com.example.filman.ui.components.atoms.FilmanSurface
import com.example.filman.ui.components.atoms.SurfaceShape
import com.example.filman.ui.components.atoms.SurfaceStyle
import com.example.filman.ui.components.molecules.EpisodeCard
import com.example.filman.ui.components.templates.DefaultBackground
import com.example.filman.ui.components.templates.ScreenTemplate
import com.example.filman.ui.core.CollectEffect
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

@Composable
fun MovieDetailsScreen(
    state: MovieDetailsState,
    onEvent: (MovieDetailsEvent) -> Unit,
) {
    val details = state.mediaDetails
    val seriesDetails = state.seriesDetails

    val contentFocusRequester = remember { FocusRequester() }
    val playButtonFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    var initialFocusSet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(details, seriesDetails) {
        if (!initialFocusSet && details != null) {
            if (details is MediaDetails.MovieOrEpisode && seriesDetails == null) {
                runCatching { playButtonFocusRequester.requestFocus() }
            } else if (seriesDetails != null && state.nextEpisode != null) {
                runCatching { nextEpisodeFocusRequester.requestFocus() }
            }
            initialFocusSet = true
        }
    }

    ScreenTemplate(
        isLoading = state.isLoading,
        background = {
            MovieDetailsBackground(details)
        },
    ) {
        if (details == null) return@ScreenTemplate

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(contentFocusRequester)
                .focusGroup()
                .focusRestorer(),
            contentPadding = PaddingValues(bottom = MaterialTheme.spacing.extraLarge),
        ) {
            item {
                MovieDetailsHeader(
                    details = details,
                    isFavorite = state.isFavorite,
                    seriesDetails = seriesDetails,
                    movieUrl = state.movieUrl,
                    onEvent = onEvent,
                    playButtonFocusRequester = playButtonFocusRequester,
                )
            }

            if (seriesDetails != null) {
                item {
                    MovieDetailsSeasons(
                        selectedSeason = state.selectedSeason,
                        seasons = seriesDetails.seasons,
                        onEvent = onEvent,
                    )
                }

                if (state.selectedSeason != null) {
                    item {
                        MovieDetailsEpisodes(
                            episodes = state.selectedSeason.episodes,
                            posterUrl = details.posterUrl,
                            nextEpisode = state.nextEpisode,
                            nextEpisodeIndex = state.nextEpisodeIndex,
                            progressMap = state.progressMap,
                            watchedSet = state.watchedSet,
                            onEvent = onEvent,
                            nextEpisodeFocusRequester = nextEpisodeFocusRequester,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieDetailsBackground(details: MediaDetails?) {
    if (details != null) {
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
    } else {
        DefaultBackground()
    }
}

@Composable
private fun MovieDetailsHeader(
    details: MediaDetails,
    isFavorite: Boolean,
    seriesDetails: MediaDetails.Series?,
    movieUrl: String,
    onEvent: (MovieDetailsEvent) -> Unit,
    playButtonFocusRequester: FocusRequester,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp, top = 50.dp, end = 70.dp, bottom = 40.dp),
    ) {
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

            Row(modifier = Modifier.padding(top = MaterialTheme.spacing.extraLarge)) {
                if (details is MediaDetails.MovieOrEpisode && seriesDetails == null) {
                    FilmanButton(
                        onClick = { onEvent(MovieDetailsEvent.PlayMovie(movieUrl)) },
                        modifier = Modifier.focusRequester(playButtonFocusRequester),
                        style = ButtonStyle.Primary,
                    ) {
                        MovieDetailsActionText(stringResource(R.string.details_watch_now))
                    }
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
                }

                FilmanButton(
                    onClick = { onEvent(MovieDetailsEvent.ToggleFavorite) },
                    style = ButtonStyle.Outlined,
                ) {
                    MovieDetailsActionText(
                        if (isFavorite) {
                            stringResource(R.string.details_remove_favorite)
                        } else {
                            stringResource(R.string.details_add_favorite)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.MovieDetailsActionText(text: String) {
    Text(
        text = text,
        modifier = Modifier.align(Alignment.CenterVertically),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun MovieDetailsSeasons(
    selectedSeason: Season?,
    seasons: List<Season>,
    onEvent: (MovieDetailsEvent) -> Unit,
) {
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

        var expanded by rememberSaveable { mutableStateOf(false) }
        BackHandler(expanded) { expanded = false }
        Box {
            FilmanSurface(
                onClick = { expanded = true },
                style = SurfaceStyle.SurfaceVariant,
                surfaceShape = SurfaceShape.Rounded,
            ) {
                Text(
                    text = selectedSeason?.name
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
                seasons.forEach { season ->
                    DropdownMenuItem(
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

@Composable
private fun MovieDetailsEpisodes(
    episodes: List<Episode>,
    posterUrl: String,
    nextEpisode: Episode?,
    nextEpisodeIndex: Int,
    progressMap: Map<String, ProgressItem>,
    watchedSet: Set<String>,
    onEvent: (MovieDetailsEvent) -> Unit,
    nextEpisodeFocusRequester: FocusRequester,
) {
    val episodesLazyRowState = rememberLazyListState()
    val onEpisodeClickStable = remember(onEvent) { { ep: Episode -> onEvent(MovieDetailsEvent.PlayEpisode(ep)) } }

    Column {
        Text(
            text = stringResource(R.string.details_episodes),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                start = 70.dp,
                bottom = MaterialTheme.spacing.medium,
            ),
            color = Color.White,
        )

        LaunchedEffect(episodes) {
            if (nextEpisodeIndex >= 0) {
                episodesLazyRowState.scrollToItem(nextEpisodeIndex)
            }
        }

        LazyRow(
            state = episodesLazyRowState,
            modifier = Modifier
                .focusGroup()
                .focusRestorer(nextEpisodeFocusRequester),
            contentPadding = PaddingValues(start = 70.dp, end = 70.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            items(episodes, key = { it.url }) { episode ->
                val progItem = progressMap[episode.url]
                val prog = progItem?.progressPercentage ?: 0f
                val isWatched = watchedSet.contains(episode.url)

                val focusModifier =
                    if (episode == nextEpisode) {
                        Modifier.focusRequester(nextEpisodeFocusRequester)
                    } else {
                        Modifier
                    }

                EpisodeCard(
                    episode = episode,
                    posterUrl = posterUrl,
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
