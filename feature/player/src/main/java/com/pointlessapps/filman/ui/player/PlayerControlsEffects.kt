package com.pointlessapps.filman.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.TiwiButton
import com.pointlessapps.filman.ui.core.PlayerConstants
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun PlayerControlsVisibilityEffect(
    mediaUrl: String?,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean,
    playButtonFocusRequester: FocusRequester,
    isOverlayMenuOpen: Boolean,
    onHideControls: () -> Unit,
    onShowControls: () -> Unit,
    visibilityTimeoutTrigger: Boolean,
) {
    val currentIsOverlayMenuOpen by rememberUpdatedState(isOverlayMenuOpen)
    LaunchedEffect(mediaUrl) {
        delay(300.milliseconds)
        if (!currentIsOverlayMenuOpen) playButtonFocusRequester.requestFocus()
    }

    val currentIsPlayingProvider by rememberUpdatedState(isPlayingProvider)
    val currentIsBufferingProvider by rememberUpdatedState(isBufferingProvider)
    val currentOnHideControls by rememberUpdatedState(onHideControls)
    val currentOnShowControls by rememberUpdatedState(onShowControls)

    LaunchedEffect(visibilityTimeoutTrigger, isOverlayMenuOpen) {
        snapshotFlow { currentIsPlayingProvider() && !currentIsBufferingProvider() }.collectLatest { shouldHide ->
            if (shouldHide) {
                delay(CONTROLS_VISIBILITY_TIMEOUT)
                currentOnHideControls()
            } else {
                currentOnShowControls()
            }
        }
    }
}

internal fun Modifier.playerControlsKeyEvent(
    areControlsVisible: Boolean,
    onToggleUiVisibility: () -> Boolean,
    onQuickSeek: (direction: Int) -> Unit,
) = onPreviewKeyEvent {
    if (it.key == Key.Back) return@onPreviewKeyEvent false

    if (!areControlsVisible) {
        if (it.key == Key.DirectionRight || it.key == Key.DirectionLeft) {
            if (it.type == KeyEventType.KeyDown) {
                onQuickSeek(if (it.key == Key.DirectionRight) 1 else -1)
            }
            return@onPreviewKeyEvent true
        }
    }

    onToggleUiVisibility()
}

@Composable
internal fun PlayerControlsBufferingPrompt(
    onSettingsClicked: (String) -> Unit,
    requestInitialFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (requestInitialFocus) focusRequester.requestFocus()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape,
                )
                .padding(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.player_still_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TiwiButton(
            modifier = Modifier.focusRequester(focusRequester),
            text = stringResource(R.string.player_video_source),
            iconRes = R.drawable.ic_settings,
            onClick = { onSettingsClicked(PlayerConstants.MENU_SOURCES_ID) },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
internal fun PlayerControlsBackHandler(
    areControlsVisible: Boolean,
    isPlayingProvider: () -> Boolean,
    toggleUiVisibility: (Boolean) -> Unit,
) {
    val currentIsPlayingProvider by rememberUpdatedState(isPlayingProvider)
    var isPlaying by remember { mutableStateOf(currentIsPlayingProvider()) }

    LaunchedEffect(Unit) {
        snapshotFlow { currentIsPlayingProvider() }.collectLatest {
            isPlaying = it
        }
    }

    BackHandler(areControlsVisible && isPlaying) {
        toggleUiVisibility(false)
    }
}

private val CONTROLS_VISIBILITY_TIMEOUT = 5.seconds
