package com.example.filman.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import com.example.filman.R
import com.example.filman.ui.components.FilmanOverlayMenuItem.Button
import com.example.filman.ui.components.FilmanOverlayMenuItem.Header
import com.example.filman.ui.components.FilmanOverlayMenuItem.NestedMenu
import com.example.filman.ui.components.FilmanOverlayMenuItem.Option
import com.example.filman.ui.components.templates.DialogOverlayTemplate
import com.example.filman.ui.core.suppressInitialKeyUp
import com.example.filman.ui.theme.spacing

@Composable
internal fun FilmanOverlayMenu(
    title: String,
    items: List<FilmanOverlayMenuItem>,
    onDismissRequest: () -> Unit,
) {
    val resources = LocalResources.current

    val backButtonFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    val titleStack = remember { mutableStateListOf(title) }
    val itemsStack = remember { mutableStateListOf(items) }
    val isRootMenu by remember { derivedStateOf { itemsStack.size == 1 } }

    var isAnimatingForward by remember { mutableStateOf(false) }

    LaunchedEffect(firstItemFocusRequester) {
        firstItemFocusRequester.requestFocus()
    }

    DialogOverlayTemplate(onDismissRequest = onDismissRequest) {
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
        ) {
            stickyHeader {
                FilmanOverlayTitleBar(
                    title = titleStack.last(),
                    showBackButton = !isRootMenu,
                    onBackClicked = popBackNestedMenu,
                    isAnimatingForward = isAnimatingForward,
                    backButtonFocusRequester = backButtonFocusRequester,
                )
            }

            itemsIndexed(itemsStack.last(), key = { _, item -> item.hashCode() }) { index, item ->
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
                            titleStack.add(resources.getString(item.label))
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
                }
            }
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                Button(
                    modifier = Modifier.focusRequester(backButtonFocusRequester),
                    onClick = onBackClicked,
                    scale = ButtonDefaults.scale(
                        focusedScale = 1f,
                        pressedScale = 0.9f,
                    ),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        pressedContainerColor = MaterialTheme.colorScheme.surface,
                        pressedContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.overlay_menu_back),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
        text = stringResource(item.label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FilmanOverlayButtonItem(
    item: Button,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        selected = false,
        onClick = item.onClick,
        headlineContent = { FilmanOverlayItemLabel(item.label) },
        scale = ListItemDefaults.scale(pressedScale = 0.9f),
    )
}

@Composable
private fun FilmanOverlayOptionItem(
    item: Option,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
        selected = item.isSelected,
        onClick = item.onClick,
        headlineContent = { FilmanOverlayItemLabel(item.label) },
        trailingContent = {
            RadioButton(
                selected = item.isSelected,
                onClick = null,
            )
        },
        scale = ListItemDefaults.scale(pressedScale = 0.9f),
    )
}

@Composable
private fun FilmanOverlayNestedMenuItem(
    item: NestedMenu,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier,
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
        scale = ListItemDefaults.scale(pressedScale = 0.9f),
    )
}

@Composable
private fun FilmanOverlayItemLabel(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )
}

internal sealed interface FilmanOverlayMenuItem {
    data class Header(
        @StringRes val label: Int,
    ) : FilmanOverlayMenuItem

    data class Button(
        @StringRes val label: Int,
        val onClick: () -> Unit,
    ) : FilmanOverlayMenuItem

    data class Option(
        @StringRes val label: Int,
        val isSelected: Boolean,
        val onClick: () -> Unit,
    ) : FilmanOverlayMenuItem

    data class NestedMenu(
        @StringRes val label: Int,
        val value: String?,
        val items: List<FilmanOverlayMenuItem>,
    ) : FilmanOverlayMenuItem
}
