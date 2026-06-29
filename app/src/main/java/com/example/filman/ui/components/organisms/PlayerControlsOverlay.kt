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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.theme.spacing

@Composable
fun PlayerControlsOverlay(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    currentPos: Long,
    duration: Long,
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
    // Format time function
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }

        // Middle (Playback Controls)
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasPrev) {
                Surface(
                    onClick = onPrev,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    modifier = Modifier.size(64.dp),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⏮",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }

            Surface(
                onClick = onPlayPauseToggle,
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                modifier = Modifier
                    .size(80.dp)
                    .focusRequester(playPauseFocusRequester),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.DarkGray.copy(
                        alpha = 0.5f,
                    ),
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            if (hasNext) {
                Surface(
                    onClick = onNext,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    modifier = Modifier.size(64.dp),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⏭",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
        }

        // Bottom Bar (Progress, Settings)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.spacing.extraLarge,
                    end = MaterialTheme.spacing.extraLarge,
                    bottom = MaterialTheme.spacing.extraLarge,
                ),
        ) {
            var isProgressBarFocused by remember { mutableStateOf(false) }
            var isSeeking by remember { mutableStateOf(false) }
            var seekPos by remember { mutableLongStateOf(0L) }

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
                                    if (isSeeking) {
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
                        .background(Color.Red),
                )
                if (isProgressBarFocused) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color.Red, CircleShape)
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
                        color = if (isSeeking) Color.Red else Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                    Text(" · ", color = Color.LightGray)
                    Text(
                        text = formatTime(duration),
                        color = Color.LightGray,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }

                Surface(
                    onClick = onSettingsClick,
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    modifier = Modifier.focusRequester(settingsButtonFocusRequester),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.DarkGray.copy(
                            alpha = 0.5f,
                        ),
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.player_settings),
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                    )
                }
            }
        }
    }
}
