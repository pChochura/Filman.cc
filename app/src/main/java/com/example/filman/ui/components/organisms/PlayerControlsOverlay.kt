package com.example.filman.ui.components.organisms

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.components.atoms.FilmanSurface
import com.example.filman.ui.components.atoms.SurfaceShape
import com.example.filman.ui.components.atoms.SurfaceStyle
import com.example.filman.ui.theme.spacing
import java.util.Locale

@Composable
fun PlayerControlsOverlay(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    currentPosProvider: () -> Long,
    durationProvider: () -> Long,
    onPlayPauseToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onInteraction: () -> Unit,
    playPauseFocusRequester: FocusRequester,
    settingsButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent,
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f),
                    ),
                ),
            ),
    ) {
        // Top Start (Title)
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(MaterialTheme.spacing.extraLarge),
        ) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.LightGray,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        // Middle (Playback Controls)
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasPrev) {
                FilmanSurface(
                    onClick = onPrev,
                    surfaceShape = SurfaceShape.Circle,
                    modifier = Modifier.size(64.dp),
                    style = SurfaceStyle.Transparent,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            FilmanSurface(
                onClick = onPlayPauseToggle,
                surfaceShape = SurfaceShape.Circle,
                modifier = Modifier
                    .size(80.dp)
                    .focusRequester(playPauseFocusRequester),
                style = SurfaceStyle.Primary,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            if (hasNext) {
                FilmanSurface(
                    onClick = onNext,
                    surfaceShape = SurfaceShape.Circle,
                    modifier = Modifier.size(64.dp),
                    style = SurfaceStyle.Transparent,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        // Bottom Bar (Progress, Settings)
        PlaybackControlPanel(
            currentPosProvider = currentPosProvider,
            durationProvider = durationProvider,
            onSeek = onSeek,
            onSettingsClick = onSettingsClick,
            onInteraction = onInteraction,
            settingsButtonFocusRequester = settingsButtonFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.extraLarge,
                    end = MaterialTheme.spacing.extraLarge,
                    bottom = MaterialTheme.spacing.extraLarge,
                ),
        )
    }
}

@Composable
fun PlaybackControlPanel(
    currentPosProvider: () -> Long,
    durationProvider: () -> Long,
    onSeek: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onInteraction: () -> Unit,
    settingsButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // Format time function
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    var isProgressBarFocused by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableLongStateOf(0L) }

    val currentPos = currentPosProvider()
    val duration = durationProvider()

    val displayPos = if (isSeeking) seekPos else currentPos
    val progressRatio =
        if (duration > 0) {
            (displayPos.toFloat() / duration.toFloat()).coerceIn(
                0f,
                1f,
            )
        } else {
            0f
        }

    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .focusable()
                .onFocusChanged { focusState ->
                    isProgressBarFocused = focusState.isFocused
                    if (!focusState.isFocused && isSeeking) {
                        isSeeking = false
                    }
                }
                .onKeyEvent { event ->
                    onInteraction()
                    if (event.type == KeyEventType.KeyDown) {
                        val repeatCount = event.nativeKeyEvent.repeatCount
                        val step =
                            if (repeatCount > 20) 60000L else if (repeatCount > 5) 30000L else 15000L

                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (!isSeeking) {
                                    isSeeking = true
                                    seekPos = currentPos
                                }
                                seekPos = (seekPos - step).coerceAtLeast(0)
                                true
                            }

                            Key.DirectionRight -> {
                                if (!isSeeking) {
                                    isSeeking = true
                                    seekPos = currentPos
                                }
                                seekPos = (seekPos + step).coerceAtMost(duration)
                                true
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                if (isSeeking && event.nativeKeyEvent.repeatCount == 0) {
                                    onSeek(seekPos)
                                    isSeeking = false
                                    true
                                } else {
                                    false
                                }
                            }

                            else -> false
                        }
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val width = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        if (isProgressBarFocused) {
                            Color.Gray
                        } else {
                            Color.DarkGray
                        },
                    ),
            )
            Box(
                modifier = Modifier
                    .width(width * progressRatio)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            if (isProgressBarFocused) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .offset(x = (width * progressRatio) - 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                Text(
                    text = formatTime(displayPos),
                    color = if (isSeeking) MaterialTheme.colorScheme.primary else Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(" · ", color = Color.LightGray)
                Text(
                    text = formatTime(duration),
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold,
                )
            }

            FilmanSurface(
                onClick = onSettingsClick,
                surfaceShape = SurfaceShape.Circle,
                modifier = Modifier.focusRequester(settingsButtonFocusRequester),
                style = SurfaceStyle.DarkTransparent,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.player_settings),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
