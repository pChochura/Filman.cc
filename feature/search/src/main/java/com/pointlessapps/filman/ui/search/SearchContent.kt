package com.pointlessapps.filman.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.tv.material3.MaterialTheme
import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.components.LoadingMoreFooter
import com.pointlessapps.filman.ui.components.sections.emptyFilterResultsSection
import com.pointlessapps.filman.ui.components.sections.errorSection
import com.pointlessapps.filman.ui.components.sections.moviesGridSection
import com.pointlessapps.filman.ui.components.sections.searchBarSection
import com.pointlessapps.filman.ui.components.sections.searchHistoryAndCategoriesSection
import com.pointlessapps.filman.ui.core.FocusRestorationState
import com.pointlessapps.filman.ui.core.LocalFocusRestorationState
import com.pointlessapps.filman.ui.core.SectionFocusRestorationId.RECOMMENDED
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun SearchScreenContent(
    state: SearchState,
    listState: LazyGridState,
    onEvent: (TiwiEvent) -> Unit,
    contentFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    onSearchRequested: (String) -> Unit,
    onItemClicked: (sectionPrefix: String, url: String, request: DetailsRequest?) -> Unit,
    onSetLastFocusedItemId: (String) -> Unit,
    focusRestorationState: FocusRestorationState,
    searchResultsFocusRequester: FocusRequester,
    textFieldFocusRequester: FocusRequester,
    historyFocusRequesters: Map<String, FocusRequester>,
) {
    val searchFieldState = rememberTextFieldState(initialText = state.query)
    val resources = LocalResources.current
    val progressMapState = rememberUpdatedState(state.shared.progressMap)
    val currentState by rememberUpdatedState(state)

    val leftItemFocusRequesters =
        remember(state.moviesSections) {
            state.moviesSections.associate { it.title to FocusRequester() }
        }

    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }
            .collectLatest { query ->
                delay(1000.milliseconds)

                if (query.isNotBlank()) {
                    if (query != currentState.query) {
                        onEvent(SearchEvent.LoadSearchData(query))
                    }
                } else if (query.isEmpty() && currentState.query.isNotEmpty()) {
                    if (currentState.filterState.isActive) {
                        onEvent(SearchEvent.ClearQuery)
                    } else {
                        onEvent(SearchEvent.ClearSearch)
                    }
                }
            }
    }

    CompositionLocalProvider(LocalFocusRestorationState provides focusRestorationState) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            state = listState,
            contentPadding =
                PaddingValues(horizontal = MaterialTheme.spacing.extraLarge)
                    .plus(PaddingValues(bottom = MaterialTheme.spacing.extraLarge)),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(contentFocusRequester),
        ) {
            searchBarSection(
                searchFieldState = searchFieldState,
                textFieldFocusRequester = textFieldFocusRequester,
                paddingValues = paddingValues,
                activeFilterCount = state.filterState.activeCount,
                onOpenFilterMenu = {
                    onSetLastFocusedItemId("search_filter_button")
                    onEvent(SearchEvent.OpenFilterMenu)
                },
                onSearchRequested = onSearchRequested,
                onClearSearch = { onEvent(SearchEvent.ClearSearch) },
            )

            searchHistoryAndCategoriesSection(
                searchFieldState = searchFieldState,
                textFieldFocusRequester = textFieldFocusRequester,
                historyFocusRequesters = historyFocusRequesters,
                showCategories =
                    state.errorMessage == null &&
                        !state.isSearching &&
                        !state.isLoadingNextPage &&
                        state.query.isBlank() &&
                        !state.filterState.isActive &&
                        state.moviesSections.isEmpty(),
                categories = state.categories,
                searchHistory = state.searchHistory,
                onCategoryClicked = { onEvent(SearchEvent.LoadSearchDataByCategory(it)) },
                onSearchRequested = onSearchRequested,
                onHistoryItemLongClicked = { onEvent(SearchEvent.OpenSearchHistoryContextMenu(it)) },
                onClearAllHistoryClicked = { onEvent(SearchEvent.ClearAllSearchHistory) },
            )

            errorSection(
                errorMessage = state.errorMessage,
                paddingValues = PaddingValues(),
                onRefresh = { onEvent(SearchEvent.RetrySearch) },
            )

            if (state.errorMessage != null) return@LazyVerticalGrid

            if (state.isSearching) {
                item(
                    key = "search_results_loading",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "LoadingMoreFooter",
                    content = { LoadingMoreFooter() },
                )
            }

            if (!state.isSearching &&
                (state.query.isNotBlank() || state.filterState.isActive) &&
                state.moviesSections.isEmpty()
            ) {
                emptyFilterResultsSection(
                    onResetFilters = {
                        if (state.filterState.isActive) {
                            onEvent(SearchEvent.ResetFilters)
                        } else {
                            searchFieldState.clearText()
                            onEvent(SearchEvent.ClearSearch)
                        }
                    },
                )
            }

            val firstFocusableSection =
                state.moviesSections.indexOfFirst { it.movies.isNotEmpty() || it.hasMore }
            state.moviesSections.forEachIndexed { index, section ->
                val leftItemFocusRequester = leftItemFocusRequesters[section.title]
                moviesGridSection(
                    title = resources.getString(section.title),
                    items = section.movies,
                    isLoadingNextPage = state.isLoadingNextPage && !state.isSearching,
                    onItemClicked = { item ->
                        onItemClicked(
                            RECOMMENDED.prefix,
                            item.movieItem.url,
                            item.movieItem.detailsRequest,
                        )
                    },
                    onItemLongClicked = { item ->
                        onSetLastFocusedItemId("${RECOMMENDED.prefix}${item.movieItem.url}")
                        onEvent(BaseEvent.OpenContextMenu(movie = item.movieItem))
                    },
                    onLoadNextPageRequest = { },
                    showLoadMoreButton = section.hasMore,
                    onShowMoreClicked = {
                        if (section.movies.isNotEmpty()) leftItemFocusRequester?.requestFocus()
                        onEvent(SearchEvent.LoadMoreForSection(section.title))
                    },
                    firstItemFocusRequester = if (index == firstFocusableSection) searchResultsFocusRequester else null,
                    leftItemFocusRequester = leftItemFocusRequester,
                    progressProvider = { progressMapState.value },
                )
            }
        }
    }
}
