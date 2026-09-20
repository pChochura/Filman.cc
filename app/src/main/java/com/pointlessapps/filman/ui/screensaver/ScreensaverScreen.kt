package com.pointlessapps.filman.ui.screensaver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.Rating
import com.pointlessapps.filman.data.scraper.TrendingMovie
import com.pointlessapps.filman.ui.components.sections.PosterSectionMetaInfo
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun ScreensaverScreen(
    onDismiss: () -> Unit,
    viewModel: ScreensaverViewModel = koinViewModel()
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        if (movies.isNotEmpty()) {
            ScreensaverBackground(movies = movies)
        }
    }
}

@Composable
private fun ScreensaverBackground(movies: List<TrendingMovie>) {
    var currentMovie by remember { mutableStateOf<TrendingMovie?>(null) }

    LaunchedEffect(movies) {
        if (movies.isEmpty()) return@LaunchedEffect
        var index = 0
        while (true) {
            currentMovie = movies[index % movies.size]
            index++
            delay(10.seconds)
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
                }
            }
        }
    }
}
