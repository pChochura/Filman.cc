package com.pointlessapps.filman.ui.screensaver

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.TiwiToast
import com.pointlessapps.filman.ui.components.sections.PosterSectionMetaInfo
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun ScreensaverScreen(
    onDismiss: () -> Unit,
    slideDuration: Long = 10_000L,
    viewModel: ScreensaverViewModel = koinViewModel(),
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .background(Color.Black),
        ) {
            if (movies.isNotEmpty()) {
                val successMsg = stringResource(R.string.screensaver_toast_added_to_watchlist)
                val removedMsg = stringResource(R.string.screensaver_toast_removed_from_watchlist)
                ScreensaverBackground(
                    movies = movies,
                    watchlist = watchlist,
                    slideDuration = slideDuration,
                    onToggleWatchlist = { movie ->
                        toastMessage = if (viewModel.toggleWatchlist(movie)) successMsg else removedMsg
                    },
                )
            }

            toastMessage?.let { message ->
                TiwiToast(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = MaterialTheme.spacing.large),
                    onDismiss = { toastMessage = null },
                )
            }
        }
    }
}

@Composable
internal fun ScreensaverBackground(
    movies: List<MovieItem>,
    watchlist: List<MovieItem>,
    slideDuration: Long,
    onToggleWatchlist: (MovieItem) -> Unit,
) {
    var index by remember(movies) { mutableStateOf(0) }
    val movie = movies[index]
    val focusRequester = remember { FocusRequester() }
    val currentMovie = rememberUpdatedState(movie)
    val currentToggle = rememberUpdatedState(onToggleWatchlist)
    val onClick = remember { { currentToggle.value(currentMovie.value) } }

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(movies, index, slideDuration) {
        delay(slideDuration)
        index = (index + 1) % movies.size
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (event.type == KeyEventType.KeyDown) {
                            val direction = if (event.key == Key.DirectionRight) 1 else -1
                            index = (index + direction + movies.size) % movies.size
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = movie,
            transitionSpec = {
                fadeIn(animationSpec = tween(1000)) togetherWith
                    fadeOut(animationSpec = tween(1000))
            },
            label = "Screensaver background",
        ) { slide ->
            val currentScaleAnimatable = remember { Animatable(1f) }
            LaunchedEffect(slide) {
                currentScaleAnimatable.animateTo(
                    targetValue = 1.05f,
                    animationSpec = tween(5000),
                )
            }
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScaleAnimatable.value
                        scaleY = currentScaleAnimatable.value
                    }
                    .alpha(0.5f)
                    .gradientForeground(),
                model = ImageRequest.Builder(LocalContext.current)
                    .data(slide.posterUrl)
                    .crossfade(true)
                    .build(),
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                .padding(bottom = MaterialTheme.spacing.extraLarge)
                .fillMaxWidth(0.6f),
            horizontalAlignment = Alignment.Start,
        ) {
            AnimatedContent(
                targetState = movie,
                transitionSpec = {
                    fadeIn(animationSpec = tween(1000)) togetherWith
                        fadeOut(animationSpec = tween(1000))
                },
                label = "Screensaver details",
            ) { slide ->
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                    Text(
                        text = slide.titlePl,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    PosterSectionMetaInfo(
                        imdbRating = slide.tiwiRating ?: slide.filmanRating ?: slide.imdbRating,
                        tiwiRating = null,
                        seasonsNumber = null,
                        duration = null,
                        year = slide.year,
                        countries = emptyList(),
                        categories = emptyList(),
                    )

                    Text(
                        modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                        text = slide.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium + MaterialTheme.spacing.small))
            WatchlistButton(
                isInWatchlist = watchlist.any { it.url == movie.url },
                focusRequester = focusRequester,
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun WatchlistButton(
    isInWatchlist: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            },
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Icon(
            if (isInWatchlist) Icons.Default.Remove else Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.padding(end = MaterialTheme.spacing.small),
        )
        Text(stringResource(if (isInWatchlist) R.string.common_remove_from_watchlist else R.string.common_add_to_watchlist))
    }
}
