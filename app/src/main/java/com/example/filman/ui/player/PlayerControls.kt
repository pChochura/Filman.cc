package com.example.filman.ui.player

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.DetailedMedia
import com.example.filman.ui.components.FilmanIconButton
import com.example.filman.ui.components.FilmanProgressBar
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.gradientForeground
import com.example.filman.ui.core.horizontalBleed
import com.example.filman.ui.core.parseDuration
import com.example.filman.ui.theme.spacing

@Composable
internal fun PlayerControls(
    detailedMedia: DetailedMedia?,
    duration: Long,
    currentPositionProvider: () -> Long,
    currentSeason: Int?,
    currentEpisode: Int?,
) {
    val playButtonFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.extraLarge),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalBleed(MaterialTheme.spacing.extraLarge)
                .gradientBackground()
                .padding(horizontal = MaterialTheme.spacing.extraLarge),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    if (currentSeason != null && currentEpisode != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                        ) {
                            Text(
                                text = stringResource(R.string.details_season, currentSeason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.details_episode, currentEpisode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = detailedMedia?.baseItem?.titlePl.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        modifier = Modifier.fillMaxWidth(0.4f),
                        text = detailedMedia?.baseItem?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Text(
                    text = stringResource(
                        R.string.details_duration,
                        currentPositionProvider().parseDuration(),
                        duration.parseDuration(),
                    ),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusProperties {
                        onEnter = { playButtonFocusRequester.requestFocus() }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                FilmanIconButton(
                    modifier = Modifier.focusRequester(playButtonFocusRequester),
                    icon = R.drawable.ic_play,
                    contentDescription = null,
                    onClick = {},
                    iconSize = 32.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )

                FilmanProgressBar(
                    modifier = Modifier.fillMaxWidth(),
                    progressProvider = { currentPositionProvider() / duration.toFloat() },
                )
            }
        }
    }
}
