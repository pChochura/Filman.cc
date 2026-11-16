package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

fun lektorSubtitleMenuItems(hide: Boolean, onSelect: (Boolean) -> Unit): List<FilmanOverlayMenuItem> =
    listOf(
        FilmanOverlayMenuItem.Header(
            id = "header_lektor_subtitle_visibility",
            label = TextValue.StringResource(R.string.player_lektor_subtitle_visibility),
        ),
        FilmanOverlayMenuItem.Option(
            id = "lektor_show_subtitles",
            label = TextValue.StringResource(R.string.player_lektor_show_subtitles),
            isSelected = !hide,
            onClick = { onSelect(false) },
        ),
        FilmanOverlayMenuItem.Option(
            id = "lektor_hide_subtitles",
            label = TextValue.StringResource(R.string.player_lektor_hide_subtitles),
            isSelected = hide,
            onClick = { onSelect(true) },
        ),
    )
