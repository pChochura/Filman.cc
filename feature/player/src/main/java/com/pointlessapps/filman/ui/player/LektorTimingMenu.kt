package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

fun lektorTimingMenuItems(offsetMs: Int, onSelect: (Int) -> Unit): List<TiwiOverlayMenuItem> {
    val labels = listOf(
        R.string.player_lektor_timing_earlier_1000,
        R.string.player_lektor_timing_earlier_500,
        R.string.player_lektor_timing_on_time,
        R.string.player_lektor_timing_later_500,
        R.string.player_lektor_timing_later_1000,
    )
    return listOf(
        TiwiOverlayMenuItem.Header(
            id = "header_lektor_timing",
            label = TextValue.StringResource(R.string.player_lektor_timing),
        ),
    ) + LektorTiming.offsetsMs.zip(labels).map { (offset, label) ->
        TiwiOverlayMenuItem.Option(
            id = "lektor_timing_$offset",
            label = TextValue.StringResource(label),
            isSelected = offsetMs == offset,
            onClick = { onSelect(offset) },
        )
    }
}

fun lektorTimingNestedMenu(offsetMs: Int, onSelect: (Int) -> Unit): TiwiOverlayMenuItem.NestedMenu {
    val labels = listOf(
        R.string.player_lektor_timing_earlier_1000,
        R.string.player_lektor_timing_earlier_500,
        R.string.player_lektor_timing_on_time,
        R.string.player_lektor_timing_later_500,
        R.string.player_lektor_timing_later_1000,
    )
    val selectedLabel = LektorTiming.offsetsMs.zip(labels).firstOrNull { it.first == offsetMs }?.second
        ?: R.string.player_lektor_timing_earlier_500
    return TiwiOverlayMenuItem.NestedMenu(
        id = "menu_lektor_timing",
        label = TextValue.StringResource(R.string.player_lektor_timing),
        value = TextValue.StringResource(selectedLabel),
        items = lektorTimingMenuItems(offsetMs, onSelect),
    )
}
