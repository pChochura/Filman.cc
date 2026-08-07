package com.pointlessapps.filman.ui.components.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.ui.components.FilmanProgressBar
import com.pointlessapps.filman.ui.components.LoadingMoreFooter
import com.pointlessapps.filman.ui.components.SectionHeader
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.core.gradientForeground
import com.pointlessapps.filman.ui.core.handleMenuAsLongClick
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.serialization.Serializable

internal fun LazyGridScope.moviesGridSection(
    title: String?,
    items: List<MoviesGridItem>,
    isLoadingNextPage: Boolean,
    onItemClicked: (MoviesGridItem) -> Unit,
    onItemLongClicked: (MoviesGridItem) -> Unit,
    onLoadNextPageRequest: () -> Unit,
    showLoadMoreButton: Boolean,
    onShowMoreClicked: () -> Unit,
    progressProvider: () -> Map<String, Float>,
    firstItemFocusRequester: FocusRequester? = null,
    leftItemFocusRequester: FocusRequester? = null,
) {
    if (items.isEmpty() && !isLoadingNextPage) return

    if (title != null) {
        item(
            key = "movies_grid_section_header_$title",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "SectionHeader",
        ) {
            SectionHeader(
                title = title,
            )
        }
    }

    val displayedItems =
        if (showLoadMoreButton && items.size % ITEM_COUNT_PER_ROW == 0 && items.isNotEmpty()) {
            items.dropLast(1)
        } else {
            items
        }

    itemsIndexed(
        items = displayedItems,
        key = { _, item -> "${title}_${item.movieItem.url}" },
        contentType = { _, _ -> "MovieItem" },
    ) { index, item ->
        if (index == displayedItems.lastIndex && !showLoadMoreButton) {
            LaunchedEffect(index) {
                onLoadNextPageRequest()
            }
        }

        var focusModifier = if (index == 0 && firstItemFocusRequester != null) {
            Modifier.focusRequester(firstItemFocusRequester)
        } else {
            Modifier
        }

        if (index == displayedItems.lastIndex && leftItemFocusRequester != null) {
            focusModifier = focusModifier.focusRequester(leftItemFocusRequester)
        }

        val ownFocusRequester = if (index % ITEM_COUNT_PER_ROW == 0) {
            remember { FocusRequester() }
        } else {
            FocusRequester.Default
        }

        MoviesGridSectionItem(
            item = item,
            progress = progressProvider()[item.movieItem.url],
            onItemClicked = { onItemClicked(item) },
            onItemLongClicked = { onItemLongClicked(item) },
            sourceLabels = item.sources,
            modifier = focusModifier
                .withFocusRestoration("${RECOMMENDED.prefix}${item.movieItem.url}")
                .then(
                    if (index % ITEM_COUNT_PER_ROW == 0) {
                        Modifier
                            .focusRequester(ownFocusRequester)
                            .focusProperties { left = ownFocusRequester }
                    } else {
                        Modifier
                    },
                )
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }

    if (showLoadMoreButton && !isLoadingNextPage) {
        item(
            key = "movies_grid_section_show_more_$title",
            contentType = "ShowMoreItem",
        ) {
            ShowMoreGridSectionItem(
                onShowMoreClicked = onShowMoreClicked,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraLarge),
            )
        }
    }

    if (isLoadingNextPage) {
        item(
            key = "movies_grid_section_loading_next_page_$title",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "LoadingMoreFooter",
        ) {
            LoadingMoreFooter()
        }
    }
}

@Composable
private fun MoviesGridSectionItem(
    item: MoviesGridItem,
    progress: Float?,
    onItemClicked: () -> Unit,
    onItemLongClicked: () -> Unit,
    sourceLabels: List<MediaSource> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .handleMenuAsLongClick(onItemLongClicked)
            .semantics(
                mergeDescendants = true,
                properties = {},
            )
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
                .data(item.movieItem.posterUrl)
                .size(100)
                .crossfade(false)
                .build(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )

        if (sourceLabels.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(MaterialTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall / 2),
            ) {
                sourceLabels.forEach { source ->
                    SourceLabel(source = source)
                }
            }
        }

        item.movieItem.filmanRating?.let { rating ->
            Row(
                modifier = Modifier
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
                    text = "%.1f".format(rating.normalizedScore),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }

        Text(
            modifier = Modifier
                .padding(MaterialTheme.spacing.medium)
                .align(Alignment.BottomStart),
            text = item.movieItem.titlePl,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SourceLabel(source: MediaSource) {
    val label = when (source) {
        MediaSource.FILMAN -> stringResource(R.string.source_filman)
        MediaSource.EKINO -> stringResource(R.string.source_ekino)
        MediaSource.ZALUKNIJ -> stringResource(R.string.source_zaluknij)
    }
    Text(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.65f))
            .padding(
                horizontal = MaterialTheme.spacing.extraSmall,
                vertical = MaterialTheme.spacing.extraSmall / 2,
            ),
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.inverseOnSurface,
    )
}

@Composable
private fun ShowMoreGridSectionItem(
    onShowMoreClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .selectablePulse(
                shape = MaterialTheme.shapes.medium,
                focusedScale = 1.1f,
                pressedScale = 1f,
            ),
        onClick = onShowMoreClicked,
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceScale.None,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.show_more),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Immutable
@Serializable
internal sealed interface MoviesGridItem {
    val movieItem: MovieItem
    val sources: List<MediaSource>

    data class Single(
        override val movieItem: MovieItem,
    ) : MoviesGridItem {
        override val sources = if (movieItem.source == MediaSource.EKINO) {
            listOf(MediaSource.EKINO)
        } else {
            emptyList()
        }
    }

    data class Group(
        override val movieItem: MovieItem,
        val alternativeSources: List<MovieItem>,
    ) : MoviesGridItem {
        override val sources = (listOf(movieItem.source) + alternativeSources.map { it.source })
            .distinct()
    }
}

@Immutable
@Serializable
internal data class MoviesSection(
    @StringRes val title: Int,
    val movies: List<MoviesGridItem>,
    val path: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
)

private const val ITEM_COUNT_PER_ROW = 5
