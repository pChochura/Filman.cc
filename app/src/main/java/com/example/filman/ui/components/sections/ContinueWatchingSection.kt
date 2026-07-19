package com.example.filman.ui.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.example.filman.R
import com.example.filman.data.model.ProgressItem
import com.example.filman.ui.components.SectionHeader
import com.example.filman.ui.core.SectionFocusRestorationId.CONTINUE_WATCHING
import com.example.filman.ui.core.border
import com.example.filman.ui.core.focusedBorder
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.sectionFocusRestorer
import com.example.filman.ui.core.withFocusRestoration
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.continueWatchingSection(
    items: List<ProgressItem>,
    onItemClicked: (ProgressItem) -> Unit,
    onItemLongClicked: (ProgressItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "continue_watching_section_header") {
        SectionHeader(
            title = stringResource(R.string.home_continue_watching),
            modifier = Modifier.animateItem(),
        )
    }

    item(key = "continue_watching_section") {
        ContinueWatchingSectionContent(
            items = items,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked,
            modifier = Modifier
                .animateItem()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }
}

@Composable
private fun ContinueWatchingSectionContent(
    items: List<ProgressItem>,
    onItemClicked: (ProgressItem) -> Unit,
    onItemLongClicked: (ProgressItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(items) { items.map { FocusRequester() } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .sectionFocusRestorer(
                sectionKeyPrefix = CONTINUE_WATCHING.prefix,
                defaultFallback = focusRequesters.firstOrNull() ?: FocusRequester.Default,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
        ) {
            items.forEachIndexed { index, item ->
                ContinueWatchingSectionItem(
                    item = item,
                    onItemClicked = { onItemClicked(item) },
                    onItemLongClicked = { onItemLongClicked(item) },
                    modifier = Modifier
                        .focusRequester(focusRequesters[index])
                        .withFocusRestoration("${CONTINUE_WATCHING.prefix}${item.url}")
                        .focusProperties {
                            if (index == 0) {
                                left = focusRequesters.last()
                            }
                            if (index == items.lastIndex) {
                                right = focusRequesters.first()
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingSectionItem(
    item: ProgressItem,
    onItemClicked: () -> Unit,
    onItemLongClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(itemWidth),
        onClick = onItemClicked,
        onLongClick = onItemLongClicked,
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceDefaults.scale(),
        border = ClickableSurfaceDefaults.border(
            border = border(),
            focusedBorder = focusedBorder(),
        ),
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .gradientBackground(),
            model = Builder(LocalContext.current)
                .data(item.posterUrl)
                .crossfade(true)
                .size(200)
                .build(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )

        Text(
            modifier = Modifier
                .padding(MaterialTheme.spacing.medium)
                .align(Alignment.BottomStart),
            text = item.titlePl,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        item.seasonEpisode?.let { badgeText ->
            Box(
                modifier = Modifier
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

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            progress = item::progressPercentage,
            drawStopIndicator = {},
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private val itemWidth = 300.dp
