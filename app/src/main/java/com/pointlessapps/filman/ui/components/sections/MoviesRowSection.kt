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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.local.ProgressManager.Companion.MARK_AS_WATCHED_PROGRESS_THRESHOLD
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.FilmanProgressBar
import com.pointlessapps.filman.ui.components.SectionHeader
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.Companion.moviesRowPrefix
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.core.handleMenuAsLongClick
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.sectionFocusRestorer
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing

internal fun LazyGridScope.moviesRowSection(
    title: String,
    items: List<MovieItem>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    progressProvider: () -> Map<String, Float>,
    firstItemFocusRequester: FocusRequester? = null,
) {
    if (items.isEmpty()) return

    item(
        key = "movies_row_section_header_$title",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "SectionHeader",
    ) {
        SectionHeader(
            title = title,
        )
    }

    item(
        key = "movies_row_section_$title",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "MoviesRowSectionContent",
    ) {
        MoviesRowSectionContent(
            title = title,
            items = items,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked,
            firstItemFocusRequester = firstItemFocusRequester,
            progressProvider = progressProvider,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }
}

@Composable
private fun MoviesRowSectionContent(
    title: String,
    items: List<MovieItem>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    firstItemFocusRequester: FocusRequester?,
    progressProvider: () -> Map<String, Float>,
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

    val sectionPrefix = moviesRowPrefix(title)

    Column(
        modifier = modifier
            .horizontalBleed(MaterialTheme.spacing.extraLarge)
            .fillMaxWidth()
            .focusGroup()
            .sectionFocusRestorer(sectionKeyPrefix = moviesRowPrefix(title)),
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
                    MoviesRowSectionItem(
                        item = item,
                        progress = progressProvider()[item.url],
                        onItemClicked = onClicked,
                        onItemLongClicked = onLongClicked,
                        modifier = Modifier
                            .focusRequester(focusRequesters[index])
                            .let {
                                if (index == 0 && firstItemFocusRequester != null) {
                                    it.focusRequester(firstItemFocusRequester)
                                } else {
                                    it
                                }
                            }
                            .withFocusRestoration("${sectionPrefix}${item.url}")
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
private fun MoviesRowSectionItem(
    item: MovieItem,
    progress: Float?,
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
                .aspectRatio(0.75f)
                .gradientForeground(),
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.posterUrl)
                .size(100)
                .crossfade(false)
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

        if (progress != null && progress < MARK_AS_WATCHED_PROGRESS_THRESHOLD) {
            FilmanProgressBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart),
                progressProvider = { progress },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                progressColor = MaterialTheme.colorScheme.primary,
            )
        }

        if (progress != null && progress >= MARK_AS_WATCHED_PROGRESS_THRESHOLD) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f)
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

private val itemWidth = 200.dp
