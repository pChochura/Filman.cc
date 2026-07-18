package com.example.filman.ui.components.sections

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import com.example.filman.ui.core.selectablePulse
import com.example.filman.ui.theme.spacing
import kotlinx.serialization.Serializable

internal fun LazyListScope.tabRowSection(
    items: List<TabRowSectionItem>,
    selectedTabId: Int,
    onTabSelected: (TabRowSectionItem) -> Unit,
) {
    item(key = "tab_row_section") {
        TabRowSectionContent(
            items = items,
            selectedTabId = selectedTabId,
            onTabSelected = onTabSelected,
            modifier = Modifier
                .animateItem()
                .padding(bottom = MaterialTheme.spacing.extraLarge),
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
    var selectedTabIndex by remember {
        mutableIntStateOf(items.indexOfFirst { it.id == selectedTabId })
    }
    val tabRects = remember { mutableStateListOf(*items.map { Rect.Zero }.toTypedArray()) }
    val animatedUnderlineStartX by animateOffsetAsState(
        targetValue = tabRects[selectedTabIndex].bottomLeft,
    )
    val animatedUnderlineEndX by animateOffsetAsState(
        targetValue = tabRects[selectedTabIndex].bottomRight,
    )

    val underlineColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.large)
            .padding(horizontal = MaterialTheme.spacing.extraLarge)
            .focusRestorer()
            .focusGroup()
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
    ) {
        items.forEachIndexed { index, item ->
            TabRowSectionItem(
                modifier = Modifier.onGloballyPositioned {
                    tabRects[index] = it.boundsInParent()
                },
                item = item,
                onItemSelected = {
                    selectedTabIndex = index
                    onTabSelected(item)
                },
            )
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
