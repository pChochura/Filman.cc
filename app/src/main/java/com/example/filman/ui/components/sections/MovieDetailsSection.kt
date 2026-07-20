package com.example.filman.ui.components.sections

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.ActorInfo
import com.example.filman.data.model.ActorRole
import com.example.filman.data.model.DetailedMedia
import com.example.filman.ui.core.horizontalBleed
import com.example.filman.ui.core.selectablePulse
import com.example.filman.ui.theme.spacing

internal fun LazyGridScope.movieDetailsSection(
    detailedMedia: DetailedMedia?,
    onActorClicked: (ActorInfo) -> Unit,
) {
    if (detailedMedia == null) return

    item(
        key = "movie_details_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "MovieDetailsContent",
    ) {
        MovieDetailsContent(
            detailedMedia = detailedMedia,
            onItemClicked = onActorClicked,
        )
    }
}

@Composable
private fun MovieDetailsContent(
    detailedMedia: DetailedMedia?,
    onItemClicked: (ActorInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        detailedMedia?.actors?.filter {
            it.role == ActorRole.DIRECTOR || it.role == ActorRole.WRITER
        }?.let {
            MovieDetailsActorsRow(
                title = stringResource(R.string.details_director_and_writers),
                items = it,
                onItemClicked = onItemClicked,
            )
        }

        detailedMedia?.actors?.filter {
            it.role != ActorRole.DIRECTOR && it.role != ActorRole.WRITER
        }?.let {
            MovieDetailsActorsRow(
                title = stringResource(R.string.details_cast),
                items = it,
                onItemClicked = onItemClicked,
            )
        }
    }
}

@Composable
private fun MovieDetailsActorsRow(
    title: String,
    items: List<ActorInfo>,
    onItemClicked: (ActorInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val lastItemFocusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalBleed(MaterialTheme.spacing.extraLarge)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                .focusProperties {
                    onEnter = { firstItemFocusRequester.requestFocus() }
                }
                .focusGroup(),
        ) {
            items.forEachIndexed { index, item ->
                MovieDetailsActorItem(
                    actorInfo = item,
                    onItemClicked = onItemClicked,
                    modifier = when (index) {
                        0 -> Modifier.focusRequester(firstItemFocusRequester)
                        items.lastIndex -> Modifier.focusRequester(lastItemFocusRequester)
                        else -> Modifier
                    }.focusProperties {
                        if (index == 0) {
                            left = lastItemFocusRequester
                        } else if (index == items.lastIndex) {
                            right = firstItemFocusRequester
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MovieDetailsActorItem(
    actorInfo: ActorInfo,
    onItemClicked: (ActorInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier
            .selectablePulse()
            .width(actorButtonWidth)
            .height(IntrinsicSize.Min),
        onClick = { onItemClicked(actorInfo) },
        colors = ButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        shape = ButtonDefaults.shape(MaterialTheme.shapes.small),
        scale = ButtonDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                modifier = Modifier
                    .size(actorAvatarSize)
                    .clip(CircleShape),
                model = actorInfo.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
            )

            Text(
                text = actorInfo.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val actorAvatarSize = 64.dp
private val actorButtonWidth = 200.dp
