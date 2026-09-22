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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.scraper.TrendingMovie
import com.pointlessapps.filman.ui.components.FilmanToast
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
                val successMsg = stringResource(R.string.toast_added_to_watchlist)
                val errorMsg = stringResource(R.string.error_media_not_found)
                ScreensaverBackground(
                    movies = movies,
                    slideDuration = slideDuration,
                    onAddToWatchlist = { movie, onResult ->
                        viewModel.addToWatchlist(movie) { success ->
                            toastMessage = if (success) successMsg else errorMsg
                            onResult(success)
                        }
                    },
                )
            }

            toastMessage?.let { message ->
                FilmanToast(
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
private fun ScreensaverBackground(
    movies: List<TrendingMovie>,
    slideDuration: Long,
    onAddToWatchlist: (TrendingMovie, (Boolean) -> Unit) -> Unit,
) {
    var currentMovie by remember { mutableStateOf<TrendingMovie?>(null) }

    LaunchedEffect(movies) {
        if (movies.isEmpty()) return@LaunchedEffect
        var index = 0
        while (true) {
            currentMovie = movies[index % movies.size]
            index++
            delay(slideDuration)
        }
    }

    AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        targetState = currentMovie,
        transitionSpec = {
            fadeIn(animationSpec = tween(1000)) togetherWith
                    fadeOut(animationSpec = tween(1000))
        },
        label = "Screensaver",
    ) { movie ->
        if (movie != null) {
            val currentScaleAnimatable = remember { Animatable(1f) }
            LaunchedEffect(movie) {
                currentScaleAnimatable.animateTo(
                    targetValue = 1.05f,
                    animationSpec = tween(5000),
                )
            }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(movie) {
                focusRequester.requestFocus()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = currentScaleAnimatable.value
                                scaleY = currentScaleAnimatable.value
                            }
                            .alpha(0.5f)
                            .gradientForeground(),
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(movie.posterUrl)
                            .crossfade(true)
                            .build(),
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = MaterialTheme.spacing.extraLarge)
                        .padding(bottom = MaterialTheme.spacing.extraLarge)
                        .fillMaxWidth(0.6f),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    Text(
                        text = movie.title,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    PosterSectionMetaInfo(
                        imdbRating = if (movie.rating > 0) {
                            Rating(movie.rating.toFloat(), 10f)
                        } else {
                            null
                        },
                        filmanRating = null,
                        seasonsNumber = null,
                        duration = null,
                        year = movie.releaseYear,
                        countries = emptyList(),
                        categories = emptyList(),
                        source = null,
                    )

                    Text(
                        modifier = Modifier.padding(top = MaterialTheme.spacing.small),
                        text = movie.overview,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                    var isAdding by remember(movie) { mutableStateOf(false) }

                    Button(
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .focusProperties {
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                            },
                        onClick = {
                            if (!isAdding) {
                                isAdding = true
                                onAddToWatchlist(movie) { success ->
                                    isAdding = false
                                }
                            }
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        if (isAdding) {
                            Text(stringResource(R.string.loading))
                        } else {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = MaterialTheme.spacing.small),
                            )
                            Text(stringResource(R.string.add_to_watchlist))
                        }
                    }
                }
            }
        }
    }
}
