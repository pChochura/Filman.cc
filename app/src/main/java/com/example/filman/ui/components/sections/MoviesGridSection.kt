package com.example.filman.ui.components.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.components.LoadingMoreFooter
import com.example.filman.ui.components.SectionHeader
import com.example.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.example.filman.ui.core.border
import com.example.filman.ui.core.focusedBorder
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.withFocusRestoration
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.moviesGridSection(
    @StringRes title: Int?,
    items: List<MovieItem>,
    isLoadingNextPage: Boolean,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    onLoadNextPageRequest: () -> Unit,
    showLoadMoreButton: Boolean,
    onShowMoreClicked: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
) {
    if (items.isEmpty() && !isLoadingNextPage) return

    val chunkedItems = items.chunked(ITEM_COUNT_PER_ROW)
        .map { MoviesChunk(it) }

    if (title != null) {
        item(key = "movies_grid_section_header_$title") {
            SectionHeader(
                title = title,
                modifier = Modifier.animateItem(),
            )
        }
    }

    itemsIndexed(
        items = chunkedItems,
        key = { _, chunk -> chunk.movies.first().url },
    ) { rowIndex, chunk ->
        val rowItems = chunk.movies
        if (rowIndex == chunkedItems.lastIndex && !showLoadMoreButton) {
            LaunchedEffect(rowIndex) {
                onLoadNextPageRequest()
            }
        }

        val isLastRow = rowIndex == chunkedItems.lastIndex

        MoviesGridSectionRow(
            isLast = isLastRow,
            rowItems = rowItems,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked,
            showMoreInThisRow = isLastRow && showLoadMoreButton,
            onShowMoreClicked = onShowMoreClicked,
            firstItemFocusRequester = if (rowIndex == 0) firstItemFocusRequester else null,
            modifier = Modifier.animateItem(),
        )
    }

    if (isLoadingNextPage) {
        item(key = "movies_grid_section_loading_next_page_$title") {
            LoadingMoreFooter()
        }
    }
}

@Composable
private fun MoviesGridSectionRow(
    isLast: Boolean,
    rowItems: List<MovieItem>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    showMoreInThisRow: Boolean,
    onShowMoreClicked: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .then(
                if (isLast) {
                    Modifier.padding(bottom = MaterialTheme.spacing.extraLarge)
                } else {
                    Modifier
                },
            )
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.extraLarge)
            .padding(bottom = MaterialTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        val displayItems = if (showMoreInThisRow && rowItems.size == ITEM_COUNT_PER_ROW) {
            rowItems.dropLast(1)
        } else {
            rowItems
        }

        displayItems.forEachIndexed { index, item ->
            val focusModifier = if (index == 0 && firstItemFocusRequester != null) {
                Modifier.focusRequester(firstItemFocusRequester)
            } else {
                Modifier
            }
            MoviesGridSectionItem(
                item = item,
                onItemClicked = { onItemClicked(item) },
                onItemLongClicked = { onItemLongClicked(item) },
                modifier = focusModifier.withFocusRestoration("${RECOMMENDED.prefix}${item.url}"),
            )
        }

        if (showMoreInThisRow) {
            ShowMoreGridSectionItem(
                onShowMoreClicked = onShowMoreClicked,
                modifier = Modifier.weight(1f),
            )
        }

        val emptySpaces = ITEM_COUNT_PER_ROW - displayItems.size - if (showMoreInThisRow) 1 else 0
        repeat(emptySpaces) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.MoviesGridSectionItem(
    item: MovieItem,
    onItemClicked: () -> Unit,
    onItemLongClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.weight(1f),
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
                .aspectRatio(0.75f)
                .gradientBackground(),
            model = item.posterUrl,
            contentScale = ContentScale.Crop,
            contentDescription = null,
        )

        item.filmanRating?.let { rating ->
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
                    text = rating.score.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }

        Text(
            modifier = Modifier
                .padding(MaterialTheme.spacing.medium)
                .align(Alignment.BottomStart),
            text = item.titlePl,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShowMoreGridSectionItem(
    onShowMoreClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onShowMoreClicked,
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceDefaults.scale(),
        border = ClickableSurfaceDefaults.border(
            border = border(),
            focusedBorder = focusedBorder(),
        ),
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
                text = "Show more",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Immutable
private data class MoviesChunk(
    val movies: List<MovieItem>,
)

@Immutable
internal data class MoviesSection(
    @StringRes val title: Int,
    val movies: List<MovieItem>,
    val path: String? = null,
    val page: Int = 1,
    val hasMore: Boolean = false,
)

private const val ITEM_COUNT_PER_ROW = 5
