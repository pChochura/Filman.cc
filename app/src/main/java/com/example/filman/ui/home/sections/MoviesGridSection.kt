package com.example.filman.ui.home.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.data.model.Movie
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.home.components.SectionHeader
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.moviesGridSection(
    @StringRes title: Int,
    items: List<Movie>,
) {
    val chunkedItems = items.chunked(ITEM_COUNT_PER_ROW)

    item(key = "movies_grid_section_header_$title") {
        SectionHeader(title)
    }

    items(chunkedItems) { items ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.extraLarge)
                .padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            items.forEach { item ->
                MoviesGridSectionItem(item = item)
            }

            repeat(ITEM_COUNT_PER_ROW - items.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.MoviesGridSectionItem(
    item: Movie,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.weight(1f),
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceDefaults.scale(),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.medium,
            ),
            focusedBorder = Border(
                border = BorderStroke(
                    width = MaterialTheme.spacing.extraSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.medium,
            ),
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

private const val ITEM_COUNT_PER_ROW = 5
