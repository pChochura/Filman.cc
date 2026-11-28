package com.pointlessapps.filman.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.TiwiButton
import com.pointlessapps.filman.ui.core.getComposeAnimationScale
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState

@Composable
internal fun PlayerControlsNextEpisodeBox(
    areControlsVisible: Boolean,
    playButtonFocusRequester: FocusRequester,
    onNextEpisodeRequested: () -> Unit,
    uiState: NextEpisodeButtonUIState,
    onDismissRequested: () -> Unit,
    onCancelTimerRequested: () -> Unit,
) {
    BackHandler(uiState.isVisible) {
        onDismissRequested()
        playButtonFocusRequester.requestFocus()
    }

    val progress = remember { Animatable(0f) }
    var timerRunning by remember { mutableStateOf(false) }
    val animationScale = getComposeAnimationScale()

    LaunchedEffect(uiState.isVisible, uiState.isTimerRunning, uiState.timerDurationMs) {
        if (uiState.isVisible && uiState.isTimerRunning && uiState.timerDurationMs > 0) {
            timerRunning = true
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (uiState.timerDurationMs / animationScale).toInt(),
                    easing = LinearEasing,
                ),
            )
            onNextEpisodeRequested()
        } else {
            timerRunning = false
            progress.snapTo(0f)
        }
    }

    PlayerControlsNextEpisodeUI(
        isVisible = uiState.isVisible,
        isSecondaryPhase = uiState.isSecondaryPhase,
        areControlsVisible = areControlsVisible,
        progress = progress,
        timerRunning = timerRunning,
        onNextEpisodeRequested = onNextEpisodeRequested,
        onStopTimer = onCancelTimerRequested,
    )
}

@Composable
internal fun PlayerControlsNextEpisodeUI(
    isVisible: Boolean,
    isSecondaryPhase: Boolean,
    areControlsVisible: Boolean,
    progress: Animatable<Float, *>,
    timerRunning: Boolean,
    onNextEpisodeRequested: () -> Unit,
    onStopTimer: () -> Unit,
) {
    val nextEpisodeButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isVisible, areControlsVisible) {
        if (isVisible && !areControlsVisible) {
            nextEpisodeButtonFocusRequester.requestFocus()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

        TiwiButton(
            text = stringResource(R.string.player_next_episode),
            iconRes = R.drawable.ic_play,
            onClick = onNextEpisodeRequested,
            modifier =
                Modifier
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && timerRunning) {
                            onStopTimer()
                            if (it.key != Key.DirectionCenter &&
                                it.key != Key.Enter &&
                                it.key != Key.NumPadEnter
                            ) {
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    .graphicsLayer {
                        clip = false
                        alpha = if (isSecondaryPhase || areControlsVisible) 1f else 0.5f
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .then(
                        if (!areControlsVisible) {
                            Modifier.drawWithCache {
                                val outline = CircleShape.createOutline(size, layoutDirection, this)
                                val progressWidth = size.width * progress.value
                                onDrawWithContent {
                                    drawOutline(
                                        outline = outline,
                                        color = backgroundColor.copy(alpha = 0.5f),
                                    )
                                    clipRect(right = progressWidth) {
                                        drawOutline(
                                            outline = outline,
                                            color = backgroundColor,
                                        )
                                    }
                                    drawContent()
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .focusRequester(nextEpisodeButtonFocusRequester),
            containerColor =
                if (areControlsVisible) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            focusedContainerColor =
                if (areControlsVisible) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            contentColor =
                if (areControlsVisible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            focusedContentColor =
                if (areControlsVisible) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun PlayerMovieRecommendationsBottomSection(
    recommendations: List<MovieItem>,
    onRecommendationClicked: (MovieItem) -> Unit,
    onDismissRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recommendations.isEmpty()) return

    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstItemFocusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.common_continue_watching),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            recommendations.take(3).forEachIndexed { index, movie ->
                val ratingStr = (movie.imdbRating?.score ?: movie.tiwiRating?.score)?.let {
                    String.format(java.util.Locale.US, "%.1f", it)
                }
                val imageModel = movie.backgroundUrl?.takeIf { it.isNotBlank() } ?: movie.posterUrl

                Surface(
                    onClick = { onRecommendationClicked(movie) },
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .then(if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier)
                        .selectablePulse(),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xFF181818),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = movie.titlePl,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.35f to Color(0x60000000),
                                        0.7f to Color(0xCC000000),
                                        1.0f to Color(0xF8000000),
                                    ),
                                ),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = movie.titlePl,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 8.dp),
                                ) {
                                    movie.year?.let { y ->
                                        Text(
                                            text = y.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.75f),
                                        )
                                    }
                                    if (ratingStr != null) {
                                        Text(
                                            text = "★ $ratingStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFFC107),
                                        )
                                    }
                                }
                            }

                            if (movie.description.isNotBlank()) {
                                Text(
                                    text = movie.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        TiwiButton(
            text = stringResource(R.string.player_roll_credits),
            iconRes = R.drawable.ic_back,
            onClick = onDismissRequested,
            modifier = Modifier
                .padding(top = 20.dp)
                .height(38.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
