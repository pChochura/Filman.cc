package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.ui.components.MediaCard
import com.pointlessapps.filman.ui.components.SectionHeader
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.CONTINUE_WATCHING
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.sectionFocusRestorer
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing

fun LazyGridScope.continueWatchingSection(
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
    val focusRequesters =
        remember(items) {
            val newDict =
                items.mapIndexed { index, it ->
                    val key = "${it.url}_$index"
                    key to focusRequestersDict.getOrPut(key) { FocusRequester() }
                }.toMap()
            focusRequestersDict.clear()
            focusRequestersDict.putAll(newDict)
            items.mapIndexed { index, it -> focusRequestersDict.getValue("${it.url}_$index") }
        }

    Column(
        modifier =
            modifier
                .horizontalBleed(MaterialTheme.spacing.extraLarge)
                .fillMaxWidth()
                .focusGroup()
                .sectionFocusRestorer(sectionKeyPrefix = CONTINUE_WATCHING.prefix),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
        ) {
            items.forEachIndexed { index, item ->
                key("${item.url}_$index") {
                    val onClicked = remember(item) { { onItemClicked(item) } }
                    val onLongClicked = remember(item) { { onItemLongClicked(item) } }
                    ContinueWatchingSectionItem(
                        item = item,
                        onItemClicked = onClicked,
                        onItemLongClicked = onLongClicked,
                        modifier =
                            Modifier
                                .focusRequester(focusRequesters[index])
                                .let {
                                    if (index == 0 && firstItemFocusRequester != null) {
                                        it.focusRequester(firstItemFocusRequester)
                                    } else {
                                        it
                                    }
                                }
                                .withFocusRestoration(
                                    itemIndex = index,
                                    items = items,
                                    sectionPrefix = CONTINUE_WATCHING.prefix,
                                    itemKeyMapper = { it.url },
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
    MediaCard(
        title =
            if (item is ProgressItem.NextEpisode) {
                stringResource(
                    R.string.home_next_episode_format,
                    item.seriesTitle ?: item.titlePl,
                )
            } else {
                item.displayTitle
            },
        posterUrl = item.posterUrl,
        aspectRatio = 1.5f,
        onItemClicked = onItemClicked,
        onItemLongClicked = onItemLongClicked,
        modifier = modifier,
        progress = item.progressPercentage,
        badgeText = item.seasonEpisode,
        width = itemWidth,
    )
}

private val itemWidth = 300.dp
