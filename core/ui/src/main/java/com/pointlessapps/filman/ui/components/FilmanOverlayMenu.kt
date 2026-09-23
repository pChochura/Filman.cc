package com.pointlessapps.filman.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem.Button
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem.Footer
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem.Header
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem.NestedMenu
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem.Option
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem.ReorderableOption
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.core.gradientBackground
import com.pointlessapps.filman.ui.core.selectablePulse
import com.pointlessapps.filman.ui.core.suppressInitialKeyUp
import com.pointlessapps.filman.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun FilmanOverlayMenu(
    title: TextValue,
    items: List<FilmanOverlayMenuItem>,
    onDismissRequest: () -> Unit,
    initialMenuId: String? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val backButtonFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    val titleStack = remember { mutableStateListOf(title) }
    val itemsStack = remember { mutableStateListOf(items) }
    val isRootMenu by remember { derivedStateOf { itemsStack.size == 1 } }

    val firstClickableIndex = itemsStack.lastOrNull()?.indexOfFirst { it !is Header }

    LaunchedEffect(items) {
        // Refresh the items on reorder
        itemsStack[0] = items
        for (i in 1 until itemsStack.size) {
            val currentTitle = titleStack[i]
            val parentItems = itemsStack[i - 1]

            val matchingItems = parentItems.firstNotNullOfOrNull { parentItem ->
                if (parentItem.label == currentTitle) {
                    when (parentItem) {
                        is NestedMenu -> parentItem.items
                        is ReorderableOption -> {
                            parentItem.trailingButtons
                                .filterIsInstance<ReorderableOption.TrailingButton.NestedMenu>()
                                .firstOrNull()?.items
                        }

                        else -> null
                    }
                } else null
            }

            if (matchingItems != null) {
                itemsStack[i] = matchingItems
            } else {
                val size = itemsStack.size
                for (j in size - 1 downTo i) {
                    itemsStack.removeAt(j)
                    titleStack.removeAt(j)
                }
                break
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialMenuId != null) {
            fun findPath(
                currentItems: List<FilmanOverlayMenuItem>,
                targetId: String,
            ): List<Pair<TextValue, List<FilmanOverlayMenuItem>>>? {
                for (item in currentItems) {
                    when (item) {
                        is NestedMenu -> {
                            if (item.id == targetId) return listOf(item.label to item.items)
                            val path = findPath(item.items, targetId)
                            if (path != null) return listOf(item.label to item.items) + path
                        }

                        is ReorderableOption -> {
                            val nested =
                                item.trailingButtons.filterIsInstance<ReorderableOption.TrailingButton.NestedMenu>()
                                    .firstOrNull()
                            if (nested != null) {
                                if (item.id == targetId) return listOf(item.label to nested.items)
                                val path = findPath(nested.items, targetId)
                                if (path != null) return listOf(item.label to nested.items) + path
                            }
                        }

                        else -> {}
                    }
                }
                return null
            }

            val path = findPath(items, initialMenuId)
            path?.forEach { (label, items) ->
                titleStack.add(label)
                itemsStack.add(items)
            }
        }
    }

    var isAnimatingForward by remember { mutableStateOf(false) }

    LaunchedEffect(firstItemFocusRequester) {
        delay(100.milliseconds)
        firstItemFocusRequester.requestFocus()
    }

    FilmanOverlayMenuDialog(onDismissRequest = onDismissRequest) {
        val popBackNestedMenu: () -> Unit = {
            isAnimatingForward = false
            titleStack.removeLastOrNull()
            itemsStack.removeLastOrNull()
            coroutineScope.launch {
                delay(100.milliseconds)
                firstItemFocusRequester.requestFocus()
            }
        }

        val clickScope = rememberFilmanOverlayClickScope(popBackNestedMenu)

        BackHandler(!isRootMenu) {
            popBackNestedMenu()
        }

        LazyColumn(
            modifier =
                Modifier
                    .clipToBounds()
                    .suppressInitialKeyUp()
                    .fillMaxSize()
                    .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding =
                PaddingValues(
                    bottom = MaterialTheme.spacing.extraLarge,
                    start = MaterialTheme.spacing.extraLarge,
                    end = MaterialTheme.spacing.extraLarge,
                ),
        ) {
            stickyHeader {
                FilmanOverlayTitleBar(
                    title = titleStack.last().asString(),
                    showBackButton = !isRootMenu,
                    onBackClicked = popBackNestedMenu,
                    isAnimatingForward = isAnimatingForward,
                    backButtonFocusRequester = backButtonFocusRequester,
                )
            }

            itemsIndexed(
                items = itemsStack.last(),
                key = { _, item -> item.id },
            ) { index, item ->
                when (item) {
                    is Header -> {
                        FilmanOverlayHeaderItem(
                            item = item,
                            modifier = Modifier.animateItem(),
                        )
                    }

                    is Button -> {
                        FilmanOverlayButtonItem(
                            item = item,
                            onClick = { item.onClick(clickScope) },
                            modifier =
                                if (index == firstClickableIndex) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    Modifier
                                }
                                    .animateItem()
                                    .focusProperties {
                                        left = backButtonFocusRequester
                                    },
                        )
                    }

                    is Option -> {
                        FilmanOverlayOptionItem(
                            item = item,
                            modifier =
                                if (index == firstClickableIndex) {
                                    Modifier.focusRequester(firstItemFocusRequester)
                                } else {
                                    Modifier
                                }
                                    .animateItem()
                                    .focusProperties {
                                        left = backButtonFocusRequester
                                    },
                        )
                    }

                    is NestedMenu -> {
                        FilmanOverlayNestedMenuItem(
                            item = item,
                            onClick = {
                                isAnimatingForward = true
                                titleStack.add(item.label)
                                itemsStack.add(item.items)
                                coroutineScope.launch {
                                    delay(100.milliseconds)
                                    firstItemFocusRequester.requestFocus()
                                }
                            },
                            modifier = if (index == firstClickableIndex) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }
                                .animateItem()
                                .focusProperties {
                                    left = backButtonFocusRequester
                                },
                        )
                    }

                    is ReorderableOption -> {
                        FilmanOverlayReorderableOptionItem(
                            item = item,
                            backButtonFocusRequester = backButtonFocusRequester,
                            onOpenNestedMenu = { nestedItems ->
                                isAnimatingForward = true
                                titleStack.add(item.label)
                                itemsStack.add(nestedItems)
                                coroutineScope.launch {
                                    delay(100.milliseconds)
                                    firstItemFocusRequester.requestFocus()
                                }
                            },
                            modifier = if (index == firstClickableIndex) {
                                Modifier.focusRequester(firstItemFocusRequester)
                            } else {
                                Modifier
                            }.animateItem(),
                        )
                    }

                    is Footer -> {
                        FilmanOverlayFooterItem(
                            item = item,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmanOverlayMenuDialog(
    onDismissRequest: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(menuWidth)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .align(Alignment.CenterEnd),
                content = content,
            )
        }
    }
}


internal val menuWidth = 400.dp
