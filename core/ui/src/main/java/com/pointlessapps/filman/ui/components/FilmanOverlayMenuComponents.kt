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
internal fun FilmanOverlayTitleBar(
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
            modifier =
                Modifier
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
internal fun FilmanOverlayHeaderItem(
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
internal fun FilmanOverlayFooterItem(
    item: Footer,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.large),
        text = item.label.asString(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun FilmanOverlayButtonItem(
    item: Button,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.selectablePulse(shape = MaterialTheme.shapes.small),
        selected = false,
        onClick = onClick,
        headlineContent = { FilmanOverlayItemLabel(item.label) },
        supportingContent = item.value?.let { value ->
            {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        },
        scale = ListItemScale.None,
        shape = ListItemDefaults.shape(shape = MaterialTheme.shapes.small),
    )
}

@Composable
internal fun FilmanOverlayOptionItem(
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
internal fun FilmanOverlayNestedMenuItem(
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
internal fun FilmanOverlayReorderableOptionItem(
    item: ReorderableOption,
    backButtonFocusRequester: FocusRequester,
    onOpenNestedMenu: (List<FilmanOverlayMenuItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isReordering by remember { mutableStateOf(false) }
    val itemFocusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListItem(
            modifier = Modifier
                .focusRequester(itemFocusRequester)
                .focusProperties { left = backButtonFocusRequester }
                .weight(1f)
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
            supportingContent = item.value?.let { value ->
                {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            },
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            val focusModifier = Modifier.focusProperties { left = itemFocusRequester }

            item.trailingButtons.forEachIndexed { index, button ->
                val isFirst = index == 0
                val isLast = index == item.trailingButtons.size - 1

                val btnModifier = Modifier
                    .then(if (isFirst) focusModifier else Modifier)
                    .then(if (isLast) Modifier.padding(end = MaterialTheme.spacing.medium) else Modifier)

                when (button) {
                    is ReorderableOption.TrailingButton.Toggle -> {
                        FilmanIconButton(
                            modifier = btnModifier,
                            icon = if (button.isEnabled) {
                                R.drawable.ic_check
                            } else {
                                R.drawable.ic_delete
                            },
                            contentDescription = button.contentDescription,
                            onClick = button.onToggle,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            showTooltip = button.contentDescription != null,
                        )
                    }

                    is ReorderableOption.TrailingButton.NestedMenu -> {
                        FilmanIconButton(
                            modifier = btnModifier,
                            icon = button.icon,
                            contentDescription = button.contentDescription,
                            onClick = { onOpenNestedMenu(button.items) },
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            showTooltip = button.contentDescription != null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilmanOverlayItemLabel(label: TextValue) {
    Text(
        text = label.asString(),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
    )
}

interface FilmanOverlayClickScope {
    fun popBack()
}

@Composable
internal fun rememberFilmanOverlayClickScope(onPopBack: () -> Unit): FilmanOverlayClickScope =
    remember {
        object : FilmanOverlayClickScope {
            override fun popBack() = onPopBack()
        }
    }

@Immutable
sealed class FilmanOverlayMenuItem {
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
        val value: String? = null,
        val onClick: FilmanOverlayClickScope.() -> Unit,
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
        val value: String? = null,
        val trailingButtons: List<TrailingButton> = emptyList(),
        val onMoveUp: (() -> Unit)? = null,
        val onMoveDown: (() -> Unit)? = null,
    ) : FilmanOverlayMenuItem() {
        sealed interface TrailingButton {
            data class Toggle(
                val isEnabled: Boolean,
                val onToggle: () -> Unit,
                val contentDescription: Int? = null,
            ) : TrailingButton

            data class NestedMenu(
                val icon: Int = R.drawable.ic_more_vert,
                val items: List<FilmanOverlayMenuItem>,
                val contentDescription: Int? = null,
            ) : TrailingButton
        }
    }
}

@Immutable
data class OverlayMenuData(
    val title: TextValue,
    val items: List<FilmanOverlayMenuItem>,
    val initialMenuId: String? = null,
)

