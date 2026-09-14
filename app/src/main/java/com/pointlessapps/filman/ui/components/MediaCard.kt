package com.pointlessapps.filman.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.core.handleMenuAsLongClick
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.theme.spacing

@Composable
fun MediaCard(
    title: String,
    posterUrl: String?,
    aspectRatio: Float,
    onItemClicked: () -> Unit,
    onItemLongClicked: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    badgeText: String? = null,
    rating: Float? = null,
    width: Dp? = null,
    isFinished: Boolean = false,
    sourceLabelsContent: (@Composable () -> Unit)? = null,
) {
    val showProgress = (progress ?: 0f) > 0f && !isFinished && (progress ?: 0f) < MARK_AS_WATCHED_PROGRESS_THRESHOLD
    val showWatched = isFinished || (progress ?: 0f) >= MARK_AS_WATCHED_PROGRESS_THRESHOLD

    Surface(
        modifier =
            modifier
                .handleMenuAsLongClick(onItemLongClicked)
                .semantics(mergeDescendants = true, properties = {})
                .let { if (width != null) it.width(width) else it }
                .selectablePulse(
                    shape = MaterialTheme.shapes.medium,
                    focusedScale = 1.1f,
                    pressedScale = 1f,
                ),
        onClick = onItemClicked,
        onLongClick = onItemLongClicked,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        scale = ClickableSurfaceScale.None,
    ) {
        AsyncImage(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .gradientForeground(),
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(posterUrl)
                    .size(200)
                    .crossfade(false)
                    .build(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )

        if (sourceLabelsContent != null) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall / 2),
            ) {
                sourceLabelsContent()
            }
        }

        if (badgeText != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(MaterialTheme.spacing.small)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f))
                        .padding(
                            horizontal = MaterialTheme.spacing.small,
                            vertical = MaterialTheme.spacing.small / 2,
                        ),
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }

        if (rating != null) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(MaterialTheme.spacing.small)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f))
                        .padding(
                            horizontal = MaterialTheme.spacing.extraSmall,
                            vertical = MaterialTheme.spacing.extraSmall / 2,
                        ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Text(
                    text = "%.1f".format(rating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }

        Text(
            modifier =
                Modifier
                    .padding(MaterialTheme.spacing.medium)
                    .align(Alignment.BottomStart),
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (showProgress) {
            FilmanProgressBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart),
                progressProvider = { progress ?: 0f },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                progressColor = MaterialTheme.colorScheme.primary,
            )
        }

        if (showWatched) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .background(MaterialTheme.colorScheme.background.copy(0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.details_watched),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
