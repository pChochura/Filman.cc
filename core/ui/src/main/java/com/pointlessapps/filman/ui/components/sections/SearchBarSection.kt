package com.pointlessapps.filman.ui.components.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.core.InterceptVoiceDictation
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.suppressInitialKeyUp
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.serialization.Serializable

fun LazyGridScope.searchBarSection(
    searchFieldState: TextFieldState,
    textFieldFocusRequester: FocusRequester,
    historyFocusRequesters: Map<String, FocusRequester>,
    paddingValues: PaddingValues,
    showCategories: Boolean,
    categories: List<FilterOption>,
    selectedCategory: FilterOption?,
    searchHistory: List<String>,
    onCategoryClicked: (FilterOption) -> Unit,
    onSearchRequested: (String) -> Unit,
    onClearSearch: () -> Unit,
    onHistoryItemLongClicked: (String) -> Unit,
    onClearAllHistoryClicked: () -> Unit,
) {
    item(
        key = "search_bar_section_header",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "SearchBarSection",
    ) {
        SearchBarSection(
            searchFieldState = searchFieldState,
            textFieldFocusRequester = textFieldFocusRequester,
            paddingValues = paddingValues,
            selectedCategory = selectedCategory,
            onSearchRequested = onSearchRequested,
            onClearSearch = onClearSearch,
        )
    }

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
private fun SearchBarSection(
    searchFieldState: TextFieldState,
    textFieldFocusRequester: FocusRequester,
    paddingValues: PaddingValues,
    selectedCategory: FilterOption?,
    onSearchRequested: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val shouldShowClearButton = searchFieldState.text.isNotEmpty() || selectedCategory != null

    InterceptVoiceDictation(
        textFieldState = searchFieldState,
        onSubmit = onSearchRequested,
    ) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding(),
                    ).padding(vertical = MaterialTheme.spacing.extraLarge)
                    .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
        ) {
            TextField(
                state = searchFieldState,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(textFieldFocusRequester)
                        .withFocusRestoration("search_bar")
                        .focusProperties { left = textFieldFocusRequester }
                        .selectablePulse(
                            shape = MaterialTheme.shapes.medium,
                            focusedScale = 1f,
                            pressedScale = 1f,
                        ),
                shape = MaterialTheme.shapes.medium,
                colors =
                    TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                    ),
                placeholder = {
                    Text(
                        text =
                            selectedCategory?.let {
                                stringResource(
                                    R.string.common_selected_category,
                                    it.label,
                                )
                            } ?: stringResource(R.string.common_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                        showKeyboardOnFocus = true,
                    ),
                onKeyboardAction = {
                    onSearchRequested(searchFieldState.text.toString())
                    keyboardController?.hide()
                },
                enabled = selectedCategory == null,
            )

            AnimatedVisibility(shouldShowClearButton) {
                IconButton(
                    modifier =
                        Modifier
                            .suppressInitialKeyUp()
                            .fillMaxHeight()
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .selectablePulse(shape = MaterialTheme.shapes.medium),
                    onClick = {
                        searchFieldState.clearText()
                        onClearSearch()
                        textFieldFocusRequester.requestFocus()
                    },
                    scale = ButtonScale.None,
                    colors =
                        IconButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    shape = ButtonDefaults.shape(MaterialTheme.shapes.medium),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                    )
                }
            }
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
                FilmanButton(
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
                FilmanButton(
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

