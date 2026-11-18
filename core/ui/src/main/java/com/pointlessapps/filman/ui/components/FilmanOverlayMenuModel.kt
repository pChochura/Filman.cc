package com.pointlessapps.filman.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.core.TextValue

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

internal fun defaultItemId(prefix: String, label: TextValue, value: TextValue? = null): String {
    val labelPart = when (label) {
        is TextValue.StringResource -> "r_${label.resId}"
        is TextValue.PluralResource -> "p_${label.resId}_${label.count}"
        is TextValue.DynamicString -> "s_${label.value}"
    }
    val valuePart = when (value) {
        is TextValue.StringResource -> "_vr_${value.resId}"
        is TextValue.PluralResource -> "_vp_${value.resId}_${value.count}"
        is TextValue.DynamicString -> "_vs_${value.value}"
        null -> ""
    }
    return "${prefix}_${labelPart}$valuePart"
}

@Immutable
sealed class FilmanOverlayMenuItem {
    abstract val id: String
    abstract val label: TextValue

    data class Header(
        override val id: String,
        override val label: TextValue,
    ) : FilmanOverlayMenuItem() {
        constructor(label: TextValue) : this(defaultItemId("hdr", label), label)
    }

    data class Footer(
        override val id: String,
        override val label: TextValue,
    ) : FilmanOverlayMenuItem() {
        constructor(label: TextValue) : this(defaultItemId("ftr", label), label)
    }

    data class Button(
        override val id: String,
        override val label: TextValue,
        val value: TextValue? = null,
        val onClick: FilmanOverlayClickScope.() -> Unit,
    ) : FilmanOverlayMenuItem() {
        constructor(
            label: TextValue,
            value: TextValue? = null,
            onClick: FilmanOverlayClickScope.() -> Unit,
        ) : this(defaultItemId("btn", label, value), label, value, onClick)

        constructor(
            id: String,
            label: TextValue,
            value: String,
            onClick: FilmanOverlayClickScope.() -> Unit,
        ) : this(id, label, TextValue.DynamicString(value), onClick)

        constructor(
            label: TextValue,
            value: String,
            onClick: FilmanOverlayClickScope.() -> Unit,
        ) : this(
            defaultItemId("btn", label, TextValue.DynamicString(value)),
            label,
            TextValue.DynamicString(value),
            onClick,
        )
    }

    data class Option(
        override val id: String,
        override val label: TextValue,
        val value: TextValue? = null,
        val isSelected: Boolean,
        val onClick: () -> Unit,
    ) : FilmanOverlayMenuItem() {
        constructor(
            label: TextValue,
            value: TextValue? = null,
            isSelected: Boolean,
            onClick: () -> Unit,
        ) : this(defaultItemId("opt", label, value), label, value, isSelected, onClick)

        constructor(
            id: String,
            label: TextValue,
            value: String,
            isSelected: Boolean,
            onClick: () -> Unit,
        ) : this(id, label, TextValue.DynamicString(value), isSelected, onClick)

        constructor(
            label: TextValue,
            value: String,
            isSelected: Boolean,
            onClick: () -> Unit,
        ) : this(
            defaultItemId("opt", label, TextValue.DynamicString(value)),
            label,
            TextValue.DynamicString(value),
            isSelected,
            onClick,
        )
    }

    data class NestedMenu(
        override val id: String,
        override val label: TextValue,
        val value: TextValue? = null,
        val items: List<FilmanOverlayMenuItem>,
    ) : FilmanOverlayMenuItem() {
        constructor(
            label: TextValue,
            value: TextValue? = null,
            items: List<FilmanOverlayMenuItem>,
        ) : this(defaultItemId("menu", label, value), label, value, items)

        constructor(
            id: String,
            label: TextValue,
            value: String,
            items: List<FilmanOverlayMenuItem>,
        ) : this(id, label, TextValue.DynamicString(value), items)

        constructor(
            label: TextValue,
            value: String,
            items: List<FilmanOverlayMenuItem>,
        ) : this(
            defaultItemId("menu", label, TextValue.DynamicString(value)),
            label,
            TextValue.DynamicString(value),
            items,
        )
    }

    data class ReorderableOption(
        override val id: String,
        override val label: TextValue,
        val value: TextValue? = null,
        val trailingButtons: List<TrailingButton> = emptyList(),
        val onMoveUp: (() -> Unit)? = null,
        val onMoveDown: (() -> Unit)? = null,
    ) : FilmanOverlayMenuItem() {
        constructor(
            label: TextValue,
            value: TextValue? = null,
            trailingButtons: List<TrailingButton> = emptyList(),
            onMoveUp: (() -> Unit)? = null,
            onMoveDown: (() -> Unit)? = null,
        ) : this(defaultItemId("reorder", label, value), label, value, trailingButtons, onMoveUp, onMoveDown)

        constructor(
            id: String,
            label: TextValue,
            value: String,
            trailingButtons: List<TrailingButton> = emptyList(),
            onMoveUp: (() -> Unit)? = null,
            onMoveDown: (() -> Unit)? = null,
        ) : this(id, label, TextValue.DynamicString(value), trailingButtons, onMoveUp, onMoveDown)

        constructor(
            label: TextValue,
            value: String,
            trailingButtons: List<TrailingButton> = emptyList(),
            onMoveUp: (() -> Unit)? = null,
            onMoveDown: (() -> Unit)? = null,
        ) : this(
            defaultItemId("reorder", label, TextValue.DynamicString(value)),
            label,
            TextValue.DynamicString(value),
            trailingButtons,
            onMoveUp,
            onMoveDown,
        )

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
