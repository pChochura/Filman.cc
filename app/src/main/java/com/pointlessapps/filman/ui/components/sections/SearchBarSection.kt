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
import androidx.compose.foundation.lazy.items
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
import com.pointlessapps.filman.R
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.ui.components.FilmanButton
import com.pointlessapps.filman.ui.core.horizontalBleed
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.suppressInitialKeyUp
import com.pointlessapps.filman.ui.core.withFocusRestoration
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.serialization.Serializable

internal fun LazyGridScope.searchBarSection(
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
                count = SKELETON_ROWS_COUNT,
                key = { "categories_grid_section_skeleton_$it" },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { "CategoriesGridSectionSkeletonRow" },
            ) {
                CategoriesGridSectionSkeletonRow(
                    index = it,
                )
            }
        }

        val chunkedCategories = categories.chunked(ITEM_COUNT_PER_ROW)
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(),
            )
            .padding(vertical = MaterialTheme.spacing.extraLarge)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        TextField(
            state = searchFieldState,
            modifier = Modifier
                .weight(1f)
                .focusRequester(textFieldFocusRequester)
                .withFocusRestoration("search_bar")
                .selectablePulse(
                    shape = MaterialTheme.shapes.medium,
                    focusedScale = 1f,
                    pressedScale = 1f,
                ),
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
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
                    text = selectedCategory?.let {
                        stringResource(
                            R.string.search_selected_category,
                            it.label,
                        )
                    } ?: stringResource(R.string.home_search_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
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
                modifier = Modifier
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
                colors = IconButtonDefaults.colors(
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
            text = stringResource(R.string.search_recently_searched),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .horizontalBleed(MaterialTheme.spacing.extraLarge)
                .padding(bottom = MaterialTheme.spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.extraLarge),
        ) {
            items(searchHistory, key = { it }) { query ->
                val focusRequester = historyFocusRequesters[query] ?: FocusRequester.Default
                FilmanButton(
                    modifier = Modifier.focusRequester(focusRequester),
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
                    text = stringResource(R.string.search_clear_all_history),
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

@Composable
private fun CategoriesGridSectionSkeletonRow(
    index: Int,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_transition")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton_translate",
    )

    val spacingExtraLarge = MaterialTheme.spacing.extraLarge
    val spacingLarge = MaterialTheme.spacing.large
    val density = LocalDensity.current
    val itemSpacingPx = remember(density, spacingLarge) {
        with(density) { spacingLarge.toPx() }
    }
    val rowSpacingPx = remember(density, spacingExtraLarge) {
        with(density) { spacingExtraLarge.toPx() }
    }

    Row(
        modifier = modifier
            .then(
                if (index == SKELETON_ROWS_COUNT - 1) {
                    Modifier.padding(bottom = MaterialTheme.spacing.extraLarge)
                } else {
                    Modifier
                },
            )
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        repeat(ITEM_COUNT_PER_ROW) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.5f)
                    .clip(MaterialTheme.shapes.medium)
                    .alpha((SKELETON_ROWS_COUNT - index) / SKELETON_ROWS_COUNT.toFloat() * 0.5f)
                    .drawWithCache {
                        val itemWidth = size.width
                        val itemHeight = size.height

                        val absoluteX = i * (itemWidth + itemSpacingPx)
                        val absoluteY = index * (itemHeight + rowSpacingPx)

                        val totalWidth =
                            (itemWidth * ITEM_COUNT_PER_ROW) + (itemSpacingPx * (ITEM_COUNT_PER_ROW - 1))
                        val totalHeight =
                            (itemHeight * SKELETON_ROWS_COUNT) + (rowSpacingPx * (SKELETON_ROWS_COUNT - 1))

                        val gradientWidth = totalWidth * 0.2f
                        val gradientHeight = totalHeight * 0.2f

                        val startX = -gradientWidth
                        val endX = totalWidth + gradientWidth
                        val startY = -gradientHeight
                        val endY = totalHeight + gradientHeight

                        onDrawBehind {
                            val currentX = startX + (endX - startX) * translateAnim
                            val currentY = startY + (endY - startY) * translateAnim

                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.DarkGray,
                                        Color.LightGray,
                                        Color.DarkGray,
                                    ),
                                    start = Offset(
                                        currentX - gradientWidth - absoluteX,
                                        currentY - gradientHeight - absoluteY,
                                    ),
                                    end = Offset(
                                        currentX + gradientWidth - absoluteX,
                                        currentY + gradientHeight - absoluteY,
                                    ),
                                ),
                            )
                        }
                    },
            )
        }
    }
}

@Composable
private fun CategoriesGridSectionRow(
    isLast: Boolean,
    rowIndex: Int,
    rowItems: List<FilterOption>,
    onItemClicked: (FilterOption) -> Unit,
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
            .padding(bottom = MaterialTheme.spacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
    ) {
        rowItems.forEachIndexed { index, item ->
            CategoriesGridSectionItem(
                item = item,
                index = rowIndex * ITEM_COUNT_PER_ROW + index,
                onItemClicked = { onItemClicked(item) },
            )
        }

        repeat(ITEM_COUNT_PER_ROW - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.CategoriesGridSectionItem(
    item: FilterOption,
    index: Int,
    onItemClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .weight(1f)
            .selectablePulse(
                shape = MaterialTheme.shapes.medium,
                focusedScale = 1.1f,
                pressedScale = 1f,
            ),
        onClick = onItemClicked,
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceScale.None,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .drawWithCache {
                    val hue1 = (index * 13) % 360f
                    val hue2 = (hue1 + 60f) % 360f
                    val gradientColors = listOf(
                        Color.hsl(hue1, 0.5f, 0.4f),
                        Color.hsl(hue2, 0.5f, 0.3f),
                    )

                    onDrawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = gradientColors,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height),
                            ),
                        )
                    }
                },
        )

        Text(
            modifier = Modifier
                .padding(MaterialTheme.spacing.medium)
                .align(Alignment.Center),
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

@Immutable
@Serializable
private data class CategoriesChunk(
    val categories: List<FilterOption>,
)

private const val ITEM_COUNT_PER_ROW = 5
private const val SKELETON_ROWS_COUNT = 3
