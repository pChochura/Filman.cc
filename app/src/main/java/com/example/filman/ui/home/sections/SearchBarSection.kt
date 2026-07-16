package com.example.filman.ui.home.sections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.data.model.FilterOption
import com.example.filman.ui.core.border
import com.example.filman.ui.core.focusedBorder
import com.example.filman.ui.core.selectableBorder
import com.example.filman.ui.theme.spacing

internal fun LazyListScope.searchBarSection(
    paddingValues: PaddingValues,
    categories: List<FilterOption>,
    onCategoryClicked: (FilterOption) -> Unit,
    onSearchRequested: (String) -> Unit,
) {
    item(key = "search_bar_section") {
        SearchBarSection(
            paddingValues = paddingValues,
            onSearchRequested = onSearchRequested,
            modifier = Modifier.animateItem(),
        )
    }

    if (categories.isEmpty()) {
        items(
            count = SKELETON_ROWS_COUNT,
            key = { "categories_grid_section_skeleton_$it" },
        ) {
            CategoriesGridSectionSkeletonRow(
                index = it,
                modifier = Modifier.animateItem(),
            )
        }
    }

    val chunkedCategories = categories.chunked(ITEM_COUNT_PER_ROW)
        .map { CategoryChunk(it) }

    itemsIndexed(
        items = chunkedCategories,
        key = { _, chunk -> chunk.categories.joinToString { it.id } },
    ) { rowIndex, chunk ->
        CategoriesGridSectionRow(
            isLast = rowIndex == chunkedCategories.lastIndex,
            rowIndex = rowIndex,
            rowItems = chunk.categories,
            onItemClicked = onCategoryClicked,
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun SearchBarSection(
    paddingValues: PaddingValues,
    onSearchRequested: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val state = rememberTextFieldState()
    var isSelected by remember { mutableStateOf(false) }

    TextField(
        state = state,
        modifier = modifier
            .padding(paddingValues)
            .padding(MaterialTheme.spacing.extraLarge)
            .fillMaxWidth()
            .onFocusChanged { isSelected = it.hasFocus }
            .selectableBorder(isSelectedProvider = { isSelected }),
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        placeholder = {
            Text(
                text = stringResource(R.string.home_search_placeholder),
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
            onSearchRequested(state.text.toString())
            keyboardController?.hide()
        },
    )
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
            .padding(horizontal = MaterialTheme.spacing.extraLarge)
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
                    .drawBehind {
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
            .padding(horizontal = MaterialTheme.spacing.extraLarge)
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
        modifier = modifier.weight(1f),
        onClick = onItemClicked,
        shape = ClickableSurfaceDefaults.shape(
            shape = MaterialTheme.shapes.medium,
        ),
        scale = ClickableSurfaceDefaults.scale(),
        border = ClickableSurfaceDefaults.border(
            border = border,
            focusedBorder = focusedBorder,
        ),
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
private data class CategoryChunk(
    val categories: List<FilterOption>,
)

private const val ITEM_COUNT_PER_ROW = 5
private const val SKELETON_ROWS_COUNT = 3
