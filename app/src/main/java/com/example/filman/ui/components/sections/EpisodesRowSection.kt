package com.example.filman.ui.components.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.filman.R
import com.example.filman.data.model.MovieItem
import com.example.filman.ui.components.SectionHeader
import com.example.filman.ui.core.SectionFocusRestorationId.Companion.moviesRowPrefix
import com.example.filman.ui.core.border
import com.example.filman.ui.core.focusedBorder
import com.example.filman.ui.core.gradientBackground
import com.example.filman.ui.core.sectionFocusRestorer
import com.example.filman.ui.core.withFocusRestoration
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.episodesRowSection(
    title: String,
    items: List<MovieItem>,
    watchedSet: Set<String>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
) {
    if (items.isEmpty()) return

    item(key = "episodes_row_section_header_$title") {
        SectionHeader(
            title = title,
            modifier = Modifier.animateItem(),
        )
    }

    item(key = "episodes_row_section_$title") {
        EpisodesRowSectionContent(
            title = title,
            items = items,
            watchedSet = watchedSet,
            onItemClicked = onItemClicked,
            onItemLongClicked = onItemLongClicked,
            modifier = Modifier
                .animateItem()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }
}

@Composable
private fun EpisodesRowSectionContent(
    title: String,
    items: List<MovieItem>,
    watchedSet: Set<String>,
    onItemClicked: (MovieItem) -> Unit,
    onItemLongClicked: (MovieItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters = remember(items) { items.map { FocusRequester() } }

    Column(
        modifier = modifier
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
                EpisodesRowSectionItem(
                    item = item,
                    isWatched = watchedSet.contains(item.url),
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
private fun EpisodesRowSectionItem(
    item: MovieItem,
    isWatched: Boolean,
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

        if (isWatched) {
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
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val itemWidth = 300.dp
