package com.pointlessapps.filman.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.ui.components.TiwiIconButton
import com.pointlessapps.filman.ui.components.TooltipPosition
import com.pointlessapps.filman.ui.core.gradientBackground
import com.pointlessapps.filman.ui.theme.spacing

@Composable
internal fun BoxScope.PlayerControlsTopBar(
    areControlsVisible: Boolean,
    detailedMedia: DetailedMedia?,
    playButtonFocusRequester: FocusRequester,
    backButtonFocusRequester: FocusRequester,
    onBackClicked: () -> Unit,
    onNextEpisodeRequested: () -> Unit,
) {
    AnimatedVisibility(
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .padding(MaterialTheme.spacing.extraLarge),
        visible = areControlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            modifier =
                Modifier
                    .focusGroup()
                    .focusProperties { down = playButtonFocusRequester },
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TiwiIconButton(
                icon = R.drawable.ic_back,
                contentDescription = R.string.overlay_menu_back,
                onClick = onBackClicked,
                iconSize = 32.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tooltipPosition = TooltipPosition.Below,
                showTooltip = areControlsVisible,
                modifier =
                    Modifier
                        .focusRequester(backButtonFocusRequester)
                        .focusProperties {
                            up = backButtonFocusRequester
                            left = backButtonFocusRequester
                            down = playButtonFocusRequester
                        },
            )

            if (detailedMedia?.baseItem?.nextEpisodeUrl != null) {
                val nextEpisodeButtonFocusRequester = remember { FocusRequester() }
                TiwiIconButton(
                    icon = R.drawable.ic_next,
                    contentDescription = R.string.player_next_episode,
                    onClick = {
                        onNextEpisodeRequested()
                        playButtonFocusRequester.requestFocus()
                    },
                    iconSize = 32.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tooltipPosition = TooltipPosition.Below,
                    showTooltip = areControlsVisible,
                    modifier =
                        Modifier
                            .focusRequester(nextEpisodeButtonFocusRequester)
                            .focusProperties {
                                up = nextEpisodeButtonFocusRequester
                                down = playButtonFocusRequester
                            },
                )
            }
        }
    }
}

@Composable
internal fun PlayerControlsBottomBar(
    detailedMedia: DetailedMedia?,
    isPlayingProvider: () -> Boolean,
    isBufferingProvider: () -> Boolean,
    durationProvider: () -> Long,
    currentPositionProvider: () -> Long,
    onPlayButtonClicked: () -> Unit,
    onSeekCommited: (Long) -> Unit,
    onSettingsClicked: (String?) -> Unit,
    playButtonFocusRequester: FocusRequester,
    backButtonFocusRequester: FocusRequester,
    areControlsVisible: Boolean,
    animatedAlpha: Float,
    modifier: Modifier = Modifier,
    nextEpisodeBox: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = animatedAlpha }
                    .gradientBackground(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(MaterialTheme.spacing.extraLarge)
                    .focusGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    space = MaterialTheme.spacing.medium,
                    alignment = Alignment.Bottom,
                ),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                PlayerControlsMediaDetails(
                    detailedMedia = detailedMedia,
                    modifier = Modifier.graphicsLayer { alpha = animatedAlpha },
                )

                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    nextEpisodeBox()
                }
            }

            Row(
                modifier = Modifier.graphicsLayer { alpha = animatedAlpha },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                val settingsButtonFocusRequester = remember { FocusRequester() }

                PlayerControlsPlayPauseButton(
                    isPlayingProvider = isPlayingProvider,
                    onPlayButtonClicked = onPlayButtonClicked,
                    playButtonFocusRequester = playButtonFocusRequester,
                    areControlsVisible = areControlsVisible,
                    modifier =
                        Modifier.focusProperties {
                            up = backButtonFocusRequester
                            left = playButtonFocusRequester
                            down = settingsButtonFocusRequester
                        },
                )

                PlayerControlsProgressBar(
                    currentPositionProvider = currentPositionProvider,
                    durationProvider = durationProvider,
                    isBufferingProvider = isBufferingProvider,
                    onSeekCommited = {
                        onSeekCommited(it)
                        playButtonFocusRequester.requestFocus()
                    },
                    onSeekDiscarded = { playButtonFocusRequester.requestFocus() },
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusProperties {
                                up = backButtonFocusRequester
                                down = settingsButtonFocusRequester
                            },
                )

                TiwiIconButton(
                    modifier =
                        Modifier
                            .focusRequester(settingsButtonFocusRequester)
                            .focusProperties {
                                up = backButtonFocusRequester
                                down = playButtonFocusRequester
                                left = playButtonFocusRequester
                                right = settingsButtonFocusRequester
                            },
                    icon = R.drawable.ic_settings,
                    contentDescription = R.string.player_settings,
                    onClick = { onSettingsClicked(null) },
                    iconSize = 32.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    showTooltip = areControlsVisible,
                )
            }
        }
    }
}

@Composable
internal fun PlayerControlsMediaDetails(
    detailedMedia: DetailedMedia?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        val seasonNumber = detailedMedia?.baseItem?.seasonNumber
        val episodeNumber = detailedMedia?.baseItem?.episodeNumber

        if (seasonNumber != null && episodeNumber != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
                Text(
                    text = stringResource(R.string.player_season, seasonNumber),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.player_episode, episodeNumber),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text =
                buildString {
                    val baseTitle = detailedMedia?.baseItem?.titlePl?.ifBlank { null }
                        ?: detailedMedia?.baseItem?.titleEn.orEmpty()
                    append(baseTitle)
                    if (detailedMedia?.baseItem?.isTrailer == true) {
                        val trailerSuffix = stringResource(R.string.player_trailer)
                        if (!baseTitle.contains("trailer", ignoreCase = true) &&
                            !baseTitle.contains("zwiastun", ignoreCase = true)
                        ) {
                            append(" - $trailerSuffix")
                        }
                    } else {
                        detailedMedia?.baseItem?.episodeTitle?.let {
                            append(" - $it")
                        }
                    }
                },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Text(
            modifier =
                Modifier
                    .padding(top = MaterialTheme.spacing.extraSmall)
                    .fillMaxWidth(0.4f),
            text = detailedMedia?.baseItem?.description.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PlayerControlsPlayPauseButton(
    isPlayingProvider: () -> Boolean,
    onPlayButtonClicked: () -> Unit,
    playButtonFocusRequester: FocusRequester,
    areControlsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    TiwiIconButton(
        modifier =
            modifier
                .focusRequester(playButtonFocusRequester)
                .focusProperties { left = playButtonFocusRequester },
        icon = if (isPlayingProvider()) R.drawable.ic_pause else R.drawable.ic_play,
        contentDescription = if (isPlayingProvider()) R.string.player_pause else R.string.player_play,
        onClick = onPlayButtonClicked,
        iconSize = 64.dp,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        showTooltip = areControlsVisible,
    )
}
