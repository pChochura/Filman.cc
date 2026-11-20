package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

fun lektorDuckingMenuItems(volume: Float, onSelect: (Float) -> Unit): List<TiwiOverlayMenuItem> {
    val labels = listOf(
        R.string.player_lektor_ducking_none,
        R.string.player_lektor_ducking_low,
        R.string.player_lektor_ducking_medium,
        R.string.player_lektor_ducking_high,
    )
    return listOf(
        TiwiOverlayMenuItem.Header(
            id = "header_lektor_ducking",
            label = TextValue.StringResource(R.string.player_lektor_ducking),
        ),
    ) + LektorDucking.levels.zip(labels).map { (level, label) ->
        TiwiOverlayMenuItem.Option(
            id = "lektor_ducking_$level",
            label = TextValue.StringResource(label),
            isSelected = volume == level,
            onClick = { onSelect(level) },
        )
    }
}

fun lektorDuckingNestedMenu(volume: Float, onSelect: (Float) -> Unit): TiwiOverlayMenuItem.NestedMenu {
    val labels = listOf(
        R.string.player_lektor_ducking_none,
        R.string.player_lektor_ducking_low,
        R.string.player_lektor_ducking_medium,
        R.string.player_lektor_ducking_high,
    )
    val selectedLabel = LektorDucking.levels.zip(labels).firstOrNull { it.first == volume }?.second
        ?: R.string.player_lektor_ducking_none
    return TiwiOverlayMenuItem.NestedMenu(
        id = "menu_lektor_ducking",
        label = TextValue.StringResource(R.string.player_lektor_ducking),
        value = TextValue.StringResource(selectedLabel),
        items = lektorDuckingMenuItems(volume, onSelect),
    )
}
