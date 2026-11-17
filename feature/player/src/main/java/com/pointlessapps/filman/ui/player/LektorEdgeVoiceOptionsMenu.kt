package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

fun lektorEdgeRateNestedMenu(
    currentRate: String,
    onSelect: (String) -> Unit,
): FilmanOverlayMenuItem.NestedMenu {
    val rateLabels = listOf(
        "-20%" to R.string.player_lektor_edge_rate_slower_20,
        "-10%" to R.string.player_lektor_edge_rate_slower_10,
        "+0%" to R.string.player_lektor_edge_rate_normal,
        "+15%" to R.string.player_lektor_edge_rate_faster_15,
        "+25%" to R.string.player_lektor_edge_rate_faster_25,
        "+35%" to R.string.player_lektor_edge_rate_faster_35,
    )
    val items = rateLabels.map { (rate, labelRes) ->
        FilmanOverlayMenuItem.Option(
            id = "lektor_edge_rate_$rate",
            label = TextValue.StringResource(labelRes),
            isSelected = currentRate == rate,
            onClick = { onSelect(rate) },
        )
    }
    val selectedLabelRes = rateLabels.firstOrNull { it.first == currentRate }?.second
        ?: R.string.player_lektor_edge_rate_faster_15
    return FilmanOverlayMenuItem.NestedMenu(
        id = "menu_lektor_edge_rate",
        label = TextValue.StringResource(R.string.player_lektor_edge_rate),
        value = TextValue.StringResource(selectedLabelRes),
        items = listOf(
            FilmanOverlayMenuItem.Header(
                id = "header_lektor_edge_rate",
                label = TextValue.StringResource(R.string.player_lektor_edge_rate),
            ),
        ) + items,
    )
}

fun lektorEdgePitchNestedMenu(
    currentPitch: String,
    onSelect: (String) -> Unit,
): FilmanOverlayMenuItem.NestedMenu {
    val pitchLabels = listOf(
        "-20Hz" to R.string.player_lektor_edge_pitch_deep_20,
        "-10Hz" to R.string.player_lektor_edge_pitch_deep_10,
        "+0Hz" to R.string.player_lektor_edge_pitch_normal,
        "+10Hz" to R.string.player_lektor_edge_pitch_high_10,
        "+20Hz" to R.string.player_lektor_edge_pitch_high_20,
    )
    val items = pitchLabels.map { (pitch, labelRes) ->
        FilmanOverlayMenuItem.Option(
            id = "lektor_edge_pitch_$pitch",
            label = TextValue.StringResource(labelRes),
            isSelected = currentPitch == pitch,
            onClick = { onSelect(pitch) },
        )
    }
    val selectedLabelRes = pitchLabels.firstOrNull { it.first == currentPitch }?.second
        ?: R.string.player_lektor_edge_pitch_normal
    return FilmanOverlayMenuItem.NestedMenu(
        id = "menu_lektor_edge_pitch",
        label = TextValue.StringResource(R.string.player_lektor_edge_pitch),
        value = TextValue.StringResource(selectedLabelRes),
        items = listOf(
            FilmanOverlayMenuItem.Header(
                id = "header_lektor_edge_pitch",
                label = TextValue.StringResource(R.string.player_lektor_edge_pitch),
            ),
        ) + items,
    )
}

fun lektorEdgeVolumeNestedMenu(
    currentVolume: String,
    onSelect: (String) -> Unit,
): FilmanOverlayMenuItem.NestedMenu {
    val volumeLabels = listOf(
        "-15%" to R.string.player_lektor_edge_volume_toned,
        "+0%" to R.string.player_lektor_edge_volume_default,
        "+15%" to R.string.player_lektor_edge_volume_boosted,
        "+30%" to R.string.player_lektor_edge_volume_max,
    )
    val items = volumeLabels.map { (volume, labelRes) ->
        FilmanOverlayMenuItem.Option(
            id = "lektor_edge_volume_$volume",
            label = TextValue.StringResource(labelRes),
            isSelected = currentVolume == volume,
            onClick = { onSelect(volume) },
        )
    }
    val selectedLabelRes = volumeLabels.firstOrNull { it.first == currentVolume }?.second
        ?: R.string.player_lektor_edge_volume_default
    return FilmanOverlayMenuItem.NestedMenu(
        id = "menu_lektor_edge_volume",
        label = TextValue.StringResource(R.string.player_lektor_edge_volume),
        value = TextValue.StringResource(selectedLabelRes),
        items = listOf(
            FilmanOverlayMenuItem.Header(
                id = "header_lektor_edge_volume",
                label = TextValue.StringResource(R.string.player_lektor_edge_volume),
            ),
        ) + items,
    )
}

fun lektorEdgeOptionsNestedMenu(
    currentRate: String,
    currentPitch: String,
    currentVolume: String,
    onSelectRate: (String) -> Unit,
    onSelectPitch: (String) -> Unit,
    onSelectVolume: (String) -> Unit,
    onTestVoice: () -> Unit = {},
): FilmanOverlayMenuItem.NestedMenu {
    return FilmanOverlayMenuItem.NestedMenu(
        id = "menu_lektor_edge_options",
        label = TextValue.StringResource(R.string.player_lektor_edge_options),
        value = TextValue.DynamicString("$currentRate, $currentPitch"),
        items = listOf(
            FilmanOverlayMenuItem.Header(
                id = "header_lektor_edge_options_desc",
                label = TextValue.StringResource(R.string.player_lektor_edge_options_desc),
            ),
            lektorEdgeRateNestedMenu(currentRate, onSelectRate),
            lektorEdgePitchNestedMenu(currentPitch, onSelectPitch),
            lektorEdgeVolumeNestedMenu(currentVolume, onSelectVolume),
            FilmanOverlayMenuItem.Button(
                id = "lektor_edge_test_voice",
                label = TextValue.StringResource(R.string.player_lektor_test_voice),
                onClick = { onTestVoice() },
            ),
        ),
    )
}
