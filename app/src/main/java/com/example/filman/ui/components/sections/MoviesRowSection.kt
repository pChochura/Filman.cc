package com.example.filman.ui.components.sections

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.components.SectionHeader
import com.example.filman.ui.core.SectionFocusRestorationId.Companion.moviesRowPrefix
import com.example.filman.ui.core.border
import com.example.filman.ui.core.focusedBorder
import com.example.filman.ui.core.gradientForeground
import com.example.filman.ui.core.horizontalBleed
import com.example.filman.ui.core.sectionFocusRestorer
import com.example.filman.ui.core.withFocusRestoration
import com.example.filman.ui.theme.spacing

internal fun LazyGridScope.moviesRowSection(
    title: String,
    items: List<MovieItem>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(items) { items.map { FocusRequester() } }

    Column(
        modifier = modifier
            .horizontalBleed(MaterialTheme.spacing.extraLarge)
            .fillMaxWidth()
            .focusGroup()
            .sectionFocusRestorer(
                sectionKeyPrefix = moviesRowPrefix(title),
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
                MoviesRowSectionItem(
                    item = item,
                    onItemClicked = { onItemClicked(item) },
                    onItemLongClicked = { onItemLongClicked(item) },
                    modifier = Modifier
                        .focusRequester(focusRequesters[index])
                        .withFocusRestoration("${moviesRowPrefix(title)}${item.url}")
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
private fun MoviesRowSectionItem(
    item: MovieItem,
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
                .aspectRatio(0.75f)
                .gradientForeground(),
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.posterUrl)
                .size(100)
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
    }
}

private val itemWidth = 200.dp
