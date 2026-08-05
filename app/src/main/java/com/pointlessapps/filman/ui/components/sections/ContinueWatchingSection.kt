package com.pointlessapps.filman.ui.components.sections

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest.Builder
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.ui.components.FilmanProgressBar
import com.pointlessapps.filman.ui.components.SectionHeader
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.CONTINUE_WATCHING
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.core.handleMenuAsLongClick
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.sectionFocusRestorer
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing

internal fun LazyGridScope.continueWatchingSection(
    items: List<ProgressItem>,
    onItemClicked: (ProgressItem) -> Unit,
    onItemLongClicked: (ProgressItem) -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    if (items.isEmpty()) return

    item(
        key = "continue_watching_section_header",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "SectionHeader",
    ) {
        SectionHeader(
            title = stringResource(R.string.home_continue_watching),
        )
    }

    item(
        key = "continue_watching_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "ContinueWatchingSectionContent",
    ) {
        ContinueWatchingSectionContent(
            items = items,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked,
            firstItemFocusRequester = firstItemFocusRequester,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }
}

@Composable
private fun ContinueWatchingSectionContent(
    items: List<ProgressItem>,
    onItemClicked: (ProgressItem) -> Unit,
    onItemLongClicked: (ProgressItem) -> Unit,
    firstItemFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val focusRequestersDict = remember { mutableMapOf<String, FocusRequester>() }
    val focusRequesters = remember(items) {
        val newDict = items.associate {
            it.url to focusRequestersDict.getOrPut(it.url) { FocusRequester() }
        }
        focusRequestersDict.clear()
        focusRequestersDict.putAll(newDict)
        items.map { focusRequestersDict.getValue(it.url) }
    }

    var lastFocusedIndex by remember { mutableIntStateOf(0) }

    val lastFocusedKey = LocalFocusRestorationState.current?.lastFocusedItemKeys?.lastOrNull()
    val isFocusLost = lastFocusedKey?.startsWith(CONTINUE_WATCHING.prefix) == true &&
            items.none { "${CONTINUE_WATCHING.prefix}${it.url}" == lastFocusedKey }
    val fallbackIndex = if (isFocusLost) lastFocusedIndex.coerceAtMost(items.lastIndex) else -1

    val defaultFallback = remember(items, lastFocusedIndex) {
        if (items.isEmpty()) return@remember FocusRequester.Default
        val fallbackIndex = lastFocusedIndex.coerceAtMost(items.lastIndex)
        focusRequestersDict[items[fallbackIndex].url] ?: FocusRequester.Default
    }

    Column(
        modifier = modifier
            .horizontalBleed(MaterialTheme.spacing.extraLarge)
            .fillMaxWidth()
            .focusGroup()
            .sectionFocusRestorer(
                sectionKeyPrefix = CONTINUE_WATCHING.prefix,
                defaultFallback = defaultFallback,
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
                key(item.url) {
                    val onClicked = remember(item) { { onItemClicked(item) } }
                    val onLongClicked = remember(item) { { onItemLongClicked(item) } }
                    val itemContent = remember(item) {
                        movableContentOf { modifier: Modifier ->
                            ContinueWatchingSectionItem(
                                item = item,
                                onItemClicked = onClicked,
                                onItemLongClicked = onLongClicked,
                                modifier = modifier,
                            )
                        }
                    }
                    itemContent(
                        Modifier
                            .focusRequester(focusRequesters[index])
                            .let {
                                if (index == 0 && firstItemFocusRequester != null) {
                                    it.focusRequester(firstItemFocusRequester)
                                } else {
                                    it
                                }
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    lastFocusedIndex = index
                                }
                            }
                            .withFocusRestoration(
                                itemKey = "${CONTINUE_WATCHING.prefix}${item.url}",
                                isFallback = index == fallbackIndex,
                            )
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
}

@Composable
private fun ContinueWatchingSectionItem(
    item: ProgressItem,
    onItemClicked: () -> Unit,
    onItemLongClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .handleMenuAsLongClick(onItemLongClicked)
            .semantics(
                mergeDescendants = true,
                properties = {},
            )
            .width(itemWidth)
            .selectablePulse(
                shape = MaterialTheme.shapes.medium,
                focusedScale = 1.1f,
                pressedScale = 1f,
            ),
        onClick = onItemClicked,
        onLongClick = onItemLongClicked,
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceScale.None,
    ) {
        AsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .gradientForeground(),
            model = Builder(LocalContext.current)
                .data(item.posterUrl)
                .size(200)
                .crossfade(false)
                .build(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )

        Text(
            modifier = Modifier
                .padding(MaterialTheme.spacing.medium)
                .align(Alignment.BottomStart),
            text = if (item is ProgressItem.NextEpisode) {
                stringResource(
                    R.string.home_next_episode_format,
                    item.seriesTitle ?: item.titlePl,
                )
            } else {
                item.displayTitle
            },
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

        if (item.progressPercentage < MARK_AS_WATCHED_PROGRESS_THRESHOLD) {
            FilmanProgressBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart),
                progressProvider = { item.progressPercentage },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                progressColor = MaterialTheme.colorScheme.primary,
            )
        }

        if (item.progressPercentage >= MARK_AS_WATCHED_PROGRESS_THRESHOLD) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .background(MaterialTheme.colorScheme.background.copy(0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.details_watched),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private val itemWidth = 300.dp
