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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.pointlessapps.filman.R
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
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun FilmanOverlayMenu(
    title: TextValue,
    items: List<FilmanOverlayMenuItem>,
    onDismissRequest: () -> Unit,
    initialMenuId: String? = null,
) {
    val backButtonFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    val titleStack = remember { mutableStateListOf(title) }
    val itemsStack = remember { mutableStateListOf(items) }
    val isRootMenu by remember { derivedStateOf { itemsStack.size == 1 } }

    LaunchedEffect(items) {
        // Refresh the items on reorder
        itemsStack[0] = items
        for (i in 1 until itemsStack.size) {
            val currentTitle = titleStack[i]
            val parentItems = itemsStack[i - 1]
            val nestedMenu = parentItems.filterIsInstance<NestedMenu>()
                .find { it.label == currentTitle }
            if (nestedMenu != null) {
                itemsStack[i] = nestedMenu.items
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
            ): List<NestedMenu>? {
                for (item in currentItems) {
                    if (item is NestedMenu) {
                        if (item.id == targetId) return listOf(item)
                        val path = findPath(item.items, targetId)
                        if (path != null) return listOf(item) + path
                    }
                }

                return null
            }

            val path = findPath(items, initialMenuId)
            path?.forEach { nested ->
                titleStack.add(nested.label)
                itemsStack.add(nested.items)
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
            firstItemFocusRequester.requestFocus()
        }

        BackHandler(!isRootMenu) {
            popBackNestedMenu()
        }

        LazyColumn(
            modifier = Modifier
                .suppressInitialKeyUp()
                .fillMaxSize()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            contentPadding = PaddingValues(
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
                    is Header -> FilmanOverlayHeaderItem(
                        item = item,
                        modifier = Modifier.animateItem(),
                    )

                    is Button -> FilmanOverlayButtonItem(
                        item = item,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                            .animateItem()
                            .focusProperties {
                                left = backButtonFocusRequester
                            },
                    )

                    is Option -> FilmanOverlayOptionItem(
                        item = item,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                            .animateItem()
                            .focusProperties {
                                left = backButtonFocusRequester
                            },
                    )

                    is NestedMenu -> FilmanOverlayNestedMenuItem(
                        item = item,
                        onClick = {
                            isAnimatingForward = true
                            titleStack.add(item.label)
                            itemsStack.add(item.items)
                            firstItemFocusRequester.requestFocus()
                        },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                            .animateItem()
                            .focusProperties {
                                left = backButtonFocusRequester
                            },
                    )

                    is ReorderableOption -> FilmanOverlayReorderableOptionItem(
                        item = item,
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }
                            .animateItem()
                            .focusProperties {
                                left = backButtonFocusRequester
                            },
                    )

                    is Footer -> FilmanOverlayFooterItem(
                        item = item,
                        modifier = Modifier.animateItem(),
                    )
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
                modifier = Modifier
                    .fillMaxHeight()
                    .width(menuWidth)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .align(Alignment.CenterEnd),
                content = content,
            )
        }
    }
}

@Composable
private fun FilmanOverlayTitleBar(
    title: String,
    showBackButton: Boolean,
    onBackClicked: () -> Unit,
    isAnimatingForward: Boolean,
    backButtonFocusRequester: FocusRequester,
) {
    val multiplier = if (isAnimatingForward) 1 else -1

    AnimatedContent(
        targetState = title to showBackButton,
        transitionSpec = {
            fadeIn() + slideInHorizontally { multiplier * it / 2 } togetherWith
                    fadeOut() + slideOutHorizontally { -multiplier * it / 2 } using
                    SizeTransform(clip = false)
        },
        contentAlignment = Alignment.Center,
    ) { (title, showBackButton) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .gradientBackground(invert = true)
                .padding(top = MaterialTheme.spacing.large),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                FilmanButton(
                    modifier = Modifier.focusRequester(backButtonFocusRequester),
                    text = stringResource(R.string.overlay_menu_back),
                    iconRes = R.drawable.ic_back,
                    onClick = onBackClicked,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(MaterialTheme.spacing.large))
        }
    }
}

@Composable
private fun FilmanOverlayHeaderItem(
    item: Header,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = item.label.asString(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FilmanOverlayFooterItem(
    item: Footer,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MaterialTheme.spacing.large),
        text = item.label.asString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FilmanOverlayButtonItem(
    item: Button,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.selectablePulse(shape = MaterialTheme.shapes.small),
        selected = false,
        onClick = item.onClick,
        headlineContent = { FilmanOverlayItemLabel(item.label) },
        scale = ListItemScale.None,
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.small),
    )
}

@Composable
private fun FilmanOverlayOptionItem(
    item: Option,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.selectablePulse(shape = MaterialTheme.shapes.small),
        selected = item.isSelected,
        onClick = item.onClick,
        headlineContent = { FilmanOverlayItemLabel(item.label) },
        trailingContent = {
            RadioButton(
                selected = item.isSelected,
                onClick = null,
            )
        },
        scale = ListItemScale.None,
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.small),
    )
}

@Composable
private fun FilmanOverlayNestedMenuItem(
    item: NestedMenu,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.selectablePulse(shape = MaterialTheme.shapes.small),
        selected = false,
        onClick = onClick,
        headlineContent = { FilmanOverlayItemLabel(item.label) },
        trailingContent = {
            Text(
                text = item.value.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        },
        scale = ListItemScale.None,
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.small),
    )
}

@Composable
private fun FilmanOverlayReorderableOptionItem(
    item: ReorderableOption,
    modifier: Modifier = Modifier,
) {
    var isReordering by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .selectablePulse(shape = MaterialTheme.shapes.small)
            .onPreviewKeyEvent { keyEvent ->
                if (isReordering && keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            item.onMoveUp?.invoke()
                            return@onPreviewKeyEvent true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            item.onMoveDown?.invoke()
                            return@onPreviewKeyEvent true
                        }

                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                            -> {
                            isReordering = false
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            },
        selected = isReordering,
        onClick = { isReordering = !isReordering },
        headlineContent = { FilmanOverlayItemLabel(item.label) },
        trailingContent = {
            if (isReordering) {
                Icon(
                    painter = painterResource(R.drawable.ic_swap),
                    contentDescription = null,
                )
            }
        },
        scale = ListItemScale.None,
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.small),
    )
}

@Composable
private fun FilmanOverlayItemLabel(label: TextValue) {
    Text(
        text = label.asString(),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Immutable
internal sealed class FilmanOverlayMenuItem {
    abstract val id: String
    abstract val label: TextValue

    data class Header(
        override val id: String = UUID.randomUUID().toString(),
        override val label: TextValue,
    ) : FilmanOverlayMenuItem()

    data class Footer(
        override val id: String = UUID.randomUUID().toString(),
        override val label: TextValue,
    ) : FilmanOverlayMenuItem()

    data class Button(
        override val id: String = UUID.randomUUID().toString(),
        override val label: TextValue,
        val onClick: () -> Unit,
    ) : FilmanOverlayMenuItem()

    data class Option(
        override val id: String = UUID.randomUUID().toString(),
        override val label: TextValue,
        val isSelected: Boolean,
        val onClick: () -> Unit,
    ) : FilmanOverlayMenuItem()

    data class NestedMenu(
        override val id: String = UUID.randomUUID().toString(),
        override val label: TextValue,
        val value: String?,
        val items: List<FilmanOverlayMenuItem>,
    ) : FilmanOverlayMenuItem()

    data class ReorderableOption(
        override val id: String = UUID.randomUUID().toString(),
        override val label: TextValue,
        val onMoveUp: (() -> Unit)? = null,
        val onMoveDown: (() -> Unit)? = null,
    ) : FilmanOverlayMenuItem()
}

@Immutable
internal data class OverlayMenuData(
    val title: TextValue,
    val items: List<FilmanOverlayMenuItem>,
    val initialMenuId: String? = null,
)

private val menuWidth = 400.dp
