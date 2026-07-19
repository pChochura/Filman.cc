package com.example.filman.ui.components.sections

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import com.example.filman.ui.core.selectablePulse
import com.example.filman.ui.theme.spacing
import kotlinx.serialization.Serializable

internal fun LazyGridScope.tabRowSection(
    items: List<TabRowSectionItem>,
    selectedTabId: Int,
    onTabSelected: (TabRowSectionItem) -> Unit,
) {
    item(
        key = "tab_row_section",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "TabRowSectionContent",
    ) {
        TabRowSectionContent(
            items = items,
            selectedTabId = selectedTabId,
            onTabSelected = onTabSelected,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraLarge),
        )
    }
}

@Composable
private fun TabRowSectionContent(
    items: List<TabRowSectionItem>,
    selectedTabId: Int,
    onTabSelected: (TabRowSectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstTabFocusRequester = remember { FocusRequester() }
    var selectedTabIndex by remember {
        mutableIntStateOf(items.indexOfFirst { it.id == selectedTabId })
    }

    val underlineColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    SubcomposeLayout(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.large)
            .focusRestorer(firstTabFocusRequester)
            .focusGroup(),
    ) { constraints ->
        val tabMeasurables = subcompose("Tabs") {
            items.forEachIndexed { index, item ->
                TabRowSectionItem(
                    item = item,
                    onItemSelected = {
                        selectedTabIndex = index
                        onTabSelected(item)
                    },
                    modifier = Modifier.focusRequester(
                        if (index == 0) firstTabFocusRequester else FocusRequester.Default,
                    ),
                )
            }
        }

        val tabConstraints = constraints.copy(minWidth = 0)
        val tabPlaceables = tabMeasurables.map { it.measure(tabConstraints) }
        var layoutWidth = 0
        var layoutHeight = 0
        val tabRects = mutableListOf<Rect>()

        tabPlaceables.forEach { placeable ->
            val rect = Rect(
                left = layoutWidth.toFloat(),
                top = 0f,
                right = (layoutWidth + placeable.width).toFloat(),
                bottom = placeable.height.toFloat(),
            )
            tabRects.add(rect)
            layoutWidth += placeable.width
            layoutHeight = maxOf(layoutHeight, placeable.height)
        }

        val indicatorMeasurables = subcompose("Indicator") {
            val targetRect = tabRects.getOrNull(selectedTabIndex) ?: Rect.Zero
            val animatedUnderlineStartX by animateOffsetAsState(
                targetValue = targetRect.bottomLeft,
                label = "animatedUnderlineStartX",
            )
            val animatedUnderlineEndX by animateOffsetAsState(
                targetValue = targetRect.bottomRight,
                label = "animatedUnderlineEndX",
            )

            Spacer(
                modifier = Modifier
                    .drawBehind {
                        drawLine(
                            color = trackColor,
                            strokeWidth = 2f,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                        )
                        drawLine(
                            color = underlineColor,
                            strokeWidth = 2f,
                            start = animatedUnderlineStartX,
                            end = animatedUnderlineEndX,
                        )
                    },
            )
        }

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else layoutWidth
        val indicatorPlaceables = indicatorMeasurables.map {
            it.measure(
                constraints.copy(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = layoutHeight,
                    maxHeight = layoutHeight,
                ),
            )
        }

        layout(width, layoutHeight) {
            indicatorPlaceables.forEach { it.placeRelative(0, 0) }

            var xPosition = 0
            tabPlaceables.forEach { placeable ->
                placeable.placeRelative(xPosition, 0)
                xPosition += placeable.width
            }
        }
    }
}

@Composable
private fun TabRowSectionItem(
    item: TabRowSectionItem,
    onItemSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier
            .padding(
                vertical = MaterialTheme.spacing.small,
                horizontal = MaterialTheme.spacing.medium,
            )
            .selectablePulse()
            .onFocusChanged { if (it.isFocused) onItemSelected() }
            .focusable(),
        text = stringResource(item.title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Immutable
@Serializable
internal data class TabRowSectionItem(
    @StringRes val title: Int,
    val id: Int,
)
