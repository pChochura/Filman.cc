package com.example.filman.ui.home.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.core.border
import com.example.filman.ui.core.focusedBorder
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.withFocusRestoration
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId
import com.example.filman.ui.home.components.LoadingMoreFooter
import com.example.filman.ui.home.components.SectionHeader
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.moviesGridSection(
    @StringRes title: Int?,
    items: List<MovieItem>,
    isLoadingNextPage: Boolean,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    onLoadNextPageRequest: () -> Unit,
) {
    if (items.isEmpty()) return

    val chunkedItems = items.chunked(ITEM_COUNT_PER_ROW)
        .map { MovieChunk(it) }

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
        key = { _, chunk -> chunk.movies.joinToString { it.url } },
    ) { index, chunk ->
        val rowItems = chunk.movies
        if (index == chunkedItems.lastIndex) {
            LaunchedEffect(index) {
                onLoadNextPageRequest()
            }
        }

        Row(
            modifier = Modifier
                .animateItem()
                .then(
                    if (index == chunkedItems.lastIndex) {
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
            rowItems.forEach { item ->
                MoviesGridSectionItem(
                    item = item,
                    onItemClicked = { onItemClicked(item) },
                    onItemLongClicked = { onItemLongClicked(item) },
                    modifier = Modifier.withFocusRestoration("${HomeSectionFocusRestorationId.RECOMMENDED.prefix}${item.url}"),
                )
            }

            repeat(ITEM_COUNT_PER_ROW - rowItems.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    if (isLoadingNextPage) {
        item(key = "movies_grid_section_loading_next_page_$title") {
            LoadingMoreFooter()
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
            border = border,
            focusedBorder = focusedBorder,
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

        Text(
            modifier = Modifier
                .padding(MaterialTheme.spacing.medium)
                .align(Alignment.BottomStart),
            text = item.titlePl,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Immutable
private data class MovieChunk(
    val movies: List<MovieItem>,
)

private const val ITEM_COUNT_PER_ROW = 5
