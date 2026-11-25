package com.pointlessapps.filman.ui.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.ui.components.TiwiButton
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.theme.spacing

fun LazyGridScope.searchHistoryAndCategoriesSection(
    searchFieldState: TextFieldState,
    textFieldFocusRequester: FocusRequester,
    historyFocusRequesters: Map<String, FocusRequester>,
    showCategories: Boolean,
    categories: List<FilterOption>,
    searchHistory: List<String>,
    onCategoryClicked: (FilterOption) -> Unit,
    onSearchRequested: (String) -> Unit,
    onHistoryItemLongClicked: (String) -> Unit,
    onClearAllHistoryClicked: () -> Unit,
) {
    if (searchHistory.isNotEmpty() && showCategories) {
        item(
            key = "search_history_section",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "SearchHistorySection",
        ) {
            SearchHistorySection(
                searchHistory = searchHistory,
                historyFocusRequesters = historyFocusRequesters,
                onHistoryItemClicked = {
                    searchFieldState.setTextAndPlaceCursorAtEnd(it)
                    textFieldFocusRequester.requestFocus()
                    onSearchRequested(it)
                },
                onHistoryItemLongClicked = onHistoryItemLongClicked,
                onClearAllHistoryClicked = onClearAllHistoryClicked,
            )
        }
    }

    if (showCategories) {
        if (searchHistory.isEmpty()) {
            item(
                key = "categories_grid_top_spacer",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
            }
        }

        if (categories.isEmpty()) {
            items(
                count = CATEGORIES_SKELETON_ROWS_COUNT,
                key = { "categories_grid_section_skeleton_$it" },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "CategoriesGridSectionSkeletonRow" },
            ) {
                CategoriesGridSectionSkeletonRow(
                    index = it,
                )
            }
        }

        val chunkedCategories =
            categories
                .chunked(CATEGORIES_ITEM_COUNT_PER_ROW)
                .map { CategoriesChunk(it) }

        itemsIndexed(
            items = chunkedCategories,
            key = { _, chunk -> chunk.categories.first().label },
            span = { _, _ -> GridItemSpan(maxLineSpan) },
            contentType = { _, _ -> "CategoriesGridSectionRow" },
        ) { rowIndex, chunk ->
            CategoriesGridSectionRow(
                isLast = rowIndex == chunkedCategories.lastIndex,
                rowIndex = rowIndex,
                rowItems = chunk.categories,
                onItemClicked = onCategoryClicked,
            )
        }
    }
}

@Composable
private fun SearchHistorySection(
    searchHistory: List<String>,
    historyFocusRequesters: Map<String, FocusRequester>,
    onHistoryItemClicked: (String) -> Unit,
    onHistoryItemLongClicked: (String) -> Unit,
    onClearAllHistoryClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = stringResource(R.string.common_recently_searched),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyRow(
            modifier =
                modifier
                    .fillMaxWidth()
                    .horizontalBleed(MaterialTheme.spacing.extraLarge)
                    .padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
        ) {
            itemsIndexed(searchHistory, key = { _, item -> item }) { index, query ->
                val focusRequester = historyFocusRequesters[query] ?: FocusRequester.Default
                TiwiButton(
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .focusProperties { if (index == 0) left = focusRequester },
                    text = query,
                    iconRes = null,
                    onClick = { onHistoryItemClicked(query) },
                    onLongClick = { onHistoryItemLongClicked(query) },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
            item(key = "clear_all_history") {
                TiwiButton(
                    text = stringResource(R.string.common_clear_all_history),
                    iconRes = null,
                    onClick = onClearAllHistoryClicked,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.error,
                    focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                    focusedContentColor = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
