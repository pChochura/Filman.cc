package com.example.filman.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.FeaturedItem
import com.example.filman.data.model.Movie
import com.example.filman.data.model.ProgressItem
import com.example.filman.ui.components.atoms.SectionHeader
import com.example.filman.ui.components.molecules.MovieCard
import com.example.filman.ui.components.molecules.ProgressCard
import com.example.filman.ui.components.organisms.FeaturedSection
import com.example.filman.ui.components.organisms.MovieGridRow
import com.example.filman.ui.home.HomeEvent
import com.example.filman.ui.theme.spacing

fun LazyListScope.searchResultsContent(
    chunkedResults: List<List<Movie>>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
) {
    item(key = "search_results_header") {
        Text(
            text = stringResource(R.string.home_search_results),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .animateItem()
                .padding(horizontal = MaterialTheme.spacing.extraLarge),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
    }

    items(
        chunkedResults.size,
        key = { index -> "search_row_${chunkedResults[index].firstOrNull()?.url ?: index}" },
    ) { rowIndex ->
        val rowItems = chunkedResults[rowIndex]
        MovieGridRow(
            movies = rowItems,
            onMovieClick = onMovieClick,
            onContextMenu = onMovieContextMenu,
            modifier = Modifier
                .animateItem()
                .padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.small,
                ),
        )
    }
}

fun LazyListScope.homeTabContent(
    featuredItems: List<FeaturedItem>,
    progressItems: List<ProgressItem>,
    favorites: List<Movie>,
    homeMovies: List<Movie>,
    onEvent: (HomeEvent) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
    onProgressClick: (ProgressItem) -> Unit,
    onProgressContextMenu: (ProgressItem) -> Unit,
) {
    if (featuredItems.isNotEmpty()) {
        item(key = "featured_section") {
            FeaturedSection(
                items = featuredItems,
                onEvent = onEvent,
                modifier = Modifier
                    .animateItem()
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.85f),
            )
        }
    }

    if (progressItems.isNotEmpty()) {
        progressSection(progressItems, onProgressClick, onProgressContextMenu)
    }

    if (favorites.isNotEmpty()) {
        favoritesSection(favorites, onMovieClick, onMovieContextMenu)
    }

    recommendedSection(homeMovies, onMovieClick, onMovieContextMenu)
}

fun LazyListScope.progressSection(
    items: List<ProgressItem>,
    onProgressClick: (ProgressItem) -> Unit,
    onProgressContextMenu: (ProgressItem) -> Unit,
) {
    item(key = "continue_watching_header") {
        SectionHeader(text = stringResource(R.string.home_continue_watching))
    }

    item(key = "continue_watching") {
        val firstItemFocusRequester = remember { FocusRequester() }

        LazyRow(
            modifier = Modifier
                .animateItem()
                .focusRestorer(firstItemFocusRequester)
                .padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
        ) {
            itemsIndexed(items, key = { _, it -> "continue_watching_${it.url}" }) { index, item ->
                ProgressCard(
                    item = item,
                    onClick = onProgressClick,
                    onLongClick = onProgressContextMenu,
                    modifier = Modifier
                        .then(
                            if (index == 0) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .animateItem()
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

fun LazyListScope.favoritesSection(
    items: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
) {
    item(key = "favourites_header") {
        SectionHeader(text = stringResource(R.string.home_favorites))
    }
    item(key = "favourites") {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
            modifier = Modifier
                .animateItem()
                .focusRestorer()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
        ) {
            items(items, key = { "favourites_${it.url}" }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = onMovieClick,
                    onLongClick = onMovieContextMenu,
                    modifier = Modifier
                        .animateItem()
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

fun LazyListScope.recommendedSection(
    items: List<Movie>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
) {
    item(key = "recommended_header") {
        Text(
            text = stringResource(R.string.home_recommended),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .animateItem()
                .padding(top = MaterialTheme.spacing.extraLarge)
                .padding(horizontal = MaterialTheme.spacing.extraLarge),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
    }
    item(key = "recommended") {
        LazyRow(
            modifier = Modifier
                .animateItem()
                .focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
        ) {
            items(items, key = { "recommended_${it.url}" }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = onMovieClick,
                    onLongClick = onMovieContextMenu,
                    modifier = Modifier
                        .animateItem()
                        .width(150.dp)
                        .height(220.dp),
                )
            }
        }
    }
}

fun LazyListScope.categoryTabContent(
    selectedTabIndex: Int,
    isMoviesLoading: Boolean,
    isSeriesLoading: Boolean,
    isKidsLoading: Boolean,
    moviesFeaturedItems: List<FeaturedItem>,
    seriesFeaturedItems: List<FeaturedItem>,
    onEvent: (HomeEvent) -> Unit,
    chunkedItems: List<List<Movie>>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
    firstItemFocusRequester: FocusRequester,
) {
    val isLoading = when (selectedTabIndex) {
        1 -> isMoviesLoading
        2 -> isSeriesLoading
        3 -> isKidsLoading
        else -> false
    }
    val featuredItems = when (selectedTabIndex) {
        1 -> moviesFeaturedItems
        2 -> seriesFeaturedItems
        else -> emptyList()
    }

    if (featuredItems.isNotEmpty()) {
        item(key = "featured_section_$selectedTabIndex") {
            FeaturedSection(
                items = featuredItems,
                onEvent = onEvent,
                modifier = Modifier
                    .animateItem()
                    .fillParentMaxWidth()
                    .fillParentMaxHeight(0.85f),
            )
        }
    }

    item(key = "top_padding") {
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
    }

    categoryGridContent(
        selectedTabIndex = selectedTabIndex,
        onEvent = onEvent,
        chunkedItems = chunkedItems,
        onMovieClick = onMovieClick,
        onMovieContextMenu = onMovieContextMenu,
        firstItemFocusRequester = firstItemFocusRequester,
        isLoading = isLoading,
    )
}

fun LazyListScope.categoryGridContent(
    selectedTabIndex: Int,
    onEvent: (HomeEvent) -> Unit,
    chunkedItems: List<List<Movie>>,
    onMovieClick: (Movie) -> Unit,
    onMovieContextMenu: (Movie) -> Unit,
    firstItemFocusRequester: FocusRequester,
    isLoading: Boolean,
) {
    items(
        chunkedItems.size,
        key = { index -> "category_row_${chunkedItems[index].firstOrNull()?.url ?: index}" },
    ) { rowIndex ->
        if ((rowIndex == chunkedItems.size - 1) && !isLoading) {
            LaunchedEffect(rowIndex) {
                onEvent(HomeEvent.LoadNextPage(selectedTabIndex))
            }
        }

        val rowItems = chunkedItems[rowIndex]
        MovieGridRow(
            movies = rowItems,
            onMovieClick = onMovieClick,
            onContextMenu = onMovieContextMenu,
            modifier = Modifier
                .animateItem()
                .padding(
                    horizontal = MaterialTheme.spacing.extraLarge,
                    vertical = MaterialTheme.spacing.small,
                ),
            firstItemModifier = if (rowIndex == 0) {
                Modifier.focusRequester(firstItemFocusRequester)
            } else {
                Modifier
            },
        )
    }

    if (isLoading) {
        item(key = "loading_more") {
            Text(
                text = stringResource(R.string.loading_more),
                modifier = Modifier
                    .animateItem()
                    .padding(MaterialTheme.spacing.extraLarge),
            )
        }
    }
}
