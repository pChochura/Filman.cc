package com.example.filman.ui.home.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.core.border
import com.example.filman.ui.core.focusedBorder
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.home.components.SectionHeader
import com.example.filman.ui.theme.spacing

import com.example.filman.ui.core.sectionFocusRestorer
import com.example.filman.ui.core.withFocusRestoration
import com.example.filman.ui.home.utils.HomeSectionFocusRestorationId

internal fun LazyListScope.moviesRowSection(
    @StringRes title: Int,
    items: List<MovieItem>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "movies_row_section_header_$title") {
        SectionHeader(
            title = title,
            modifier = Modifier.animateItem(),
        )
    }

    item(key = "movies_row_section_$title") {
        MoviesRowSectionContent(
            title = title,
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
private fun MoviesRowSectionContent(
    @StringRes title: Int,
    items: List<MovieItem>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(items) { items.map { FocusRequester() } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .sectionFocusRestorer(HomeSectionFocusRestorationId.moviesRowPrefix(title), focusRequesters.firstOrNull() ?: FocusRequester.Default),
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
                        .withFocusRestoration("${HomeSectionFocusRestorationId.moviesRowPrefix(title)}${item.url}"),
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

private val itemWidth = 200.dp
