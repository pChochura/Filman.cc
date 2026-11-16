package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

fun systemVoiceMenuItems(
    voices: SystemVoiceList,
    selectedVoiceId: String,
    onSelect: (String) -> Unit,
    onPreview: (String?) -> Unit,
    onRefresh: () -> Unit,
): List<FilmanOverlayMenuItem> = buildList {
    add(FilmanOverlayMenuItem.Header(
        id = "header_lektor_system_voices",
        label = TextValue.StringResource(R.string.player_lektor_system_voices),
    ))
    if (voices.loading || voices.failed || voices.names.isEmpty()) {
        val message = when {
            voices.loading -> R.string.player_lektor_system_voices_loading
            voices.failed -> R.string.player_lektor_system_voices_error
            else -> R.string.player_lektor_system_voices_empty
        }
        add(FilmanOverlayMenuItem.Header(
            id = "lektor_system_voices_status",
            label = TextValue.StringResource(message),
        ))
    }
    voices.names.forEach { name ->
        val id = LektorVoice.systemVoiceId(name)
        add(FilmanOverlayMenuItem.Option(
            id = "lektor_voice_$id",
            label = TextValue.DynamicString(name),
            isSelected = selectedVoiceId == id,
            onClick = { onSelect(id) },
        ))
    }
    val selectedName = LektorVoice.systemVoiceName(selectedVoiceId)
    if (!voices.loading && !voices.failed && selectedName != null && selectedName !in voices.names) {
        add(FilmanOverlayMenuItem.Header(
            id = "lektor_system_selected_missing",
            label = TextValue.StringResource(R.string.player_lektor_system_selected_missing),
        ))
    }
    if (!voices.loading && !voices.failed && voices.names.isNotEmpty() &&
        (selectedName == null || selectedName in voices.names)
    ) {
        add(FilmanOverlayMenuItem.Button(
            id = "lektor_system_preview",
            label = TextValue.StringResource(R.string.player_lektor_system_preview),
            value = null,
            onClick = { onPreview(selectedName) },
        ))
    }
    add(FilmanOverlayMenuItem.Button(
        id = "lektor_system_refresh",
        label = TextValue.StringResource(R.string.player_lektor_system_refresh),
        value = null,
        onClick = { onRefresh() },
    ))
}
