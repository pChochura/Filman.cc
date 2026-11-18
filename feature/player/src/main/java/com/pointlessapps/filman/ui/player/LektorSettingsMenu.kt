package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

fun buildLektorMenuItems(
    isLektorEnabled: Boolean,
    lektorVoiceId: String,
    downloadedVoices: Set<String>,
    downloadProgress: Float?,
    isExtracting: Boolean,
    systemVoices: SystemVoiceList,
    lektorHideSubtitles: Boolean,
    lektorTimingOffsetMs: Int,
    lektorDuckingVolume: Float,
    lektorEdgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
    lektorEdgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
    lektorEdgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    onToggleLektor: (Boolean) -> Unit,
    onSelectLektorVoice: (String) -> Unit,
    onDownloadLektorModel: (String) -> Unit,
    onDeleteLektorModel: (String) -> Unit,
    onPreviewSystemVoice: (String?) -> Unit,
    onRefreshSystemVoices: () -> Unit,
    onSetLektorHideSubtitles: (Boolean) -> Unit,
    onChangeLektorTimingOffset: (Int) -> Unit,
    onChangeLektorDuckingVolume: (Float) -> Unit,
    onChangeLektorEdgeRate: (String) -> Unit = {},
    onChangeLektorEdgePitch: (String) -> Unit = {},
    onChangeLektorEdgeVolume: (String) -> Unit = {},
    onTestVoice: () -> Unit = {},
): List<FilmanOverlayMenuItem> = buildList {
    add(
        FilmanOverlayMenuItem.Header(
            id = "header_lektor_desc",
            label = TextValue.StringResource(R.string.player_lektor_description),
        ),
    )

    add(
        FilmanOverlayMenuItem.Option(
            id = "lektor_off",
            label = TextValue.StringResource(R.string.player_lektor_disabled),
            isSelected = !isLektorEnabled,
            onClick = { onToggleLektor(false) },
        ),
    )
    add(
        FilmanOverlayMenuItem.Option(
            id = "lektor_on",
            label = TextValue.StringResource(R.string.player_lektor_enabled),
            isSelected = isLektorEnabled,
            onClick = { onToggleLektor(true) },
        ),
    )

    add(
        FilmanOverlayMenuItem.Header(
            id = "header_lektor_voice",
            label = TextValue.StringResource(R.string.player_lektor_voice),
        ),
    )

    val selectedVoice = LektorVoice.fromId(lektorVoiceId)

    addOnlineVoiceItems(
        lektorVoiceId = lektorVoiceId,
        lektorEdgeRate = lektorEdgeRate,
        lektorEdgePitch = lektorEdgePitch,
        lektorEdgeVolume = lektorEdgeVolume,
        onSelectLektorVoice = onSelectLektorVoice,
        onChangeLektorEdgeRate = onChangeLektorEdgeRate,
        onChangeLektorEdgePitch = onChangeLektorEdgePitch,
        onChangeLektorEdgeVolume = onChangeLektorEdgeVolume,
        onTestVoice = onTestVoice,
    )

    addSystemVoiceItems(
        lektorVoiceId = lektorVoiceId,
        selectedVoice = selectedVoice,
        systemVoices = systemVoices,
        onSelectLektorVoice = onSelectLektorVoice,
        onPreviewSystemVoice = onPreviewSystemVoice,
        onRefreshSystemVoices = onRefreshSystemVoices,
    )

    addLocalVoiceItems(
        lektorVoiceId = lektorVoiceId,
        selectedVoice = selectedVoice,
        downloadedVoices = downloadedVoices,
        downloadProgress = downloadProgress,
        isExtracting = isExtracting,
        onSelectLektorVoice = onSelectLektorVoice,
        onDownloadLektorModel = onDownloadLektorModel,
        onDeleteLektorModel = onDeleteLektorModel,
        onTestVoice = onTestVoice,
    )

    add(
        FilmanOverlayMenuItem.Button(
            id = "lektor_test_voice",
            label = TextValue.StringResource(R.string.player_lektor_test_voice),
            value = TextValue.DynamicString(selectedVoice.displayName),
            onClick = { onTestVoice() },
        ),
    )

    addAll(lektorSubtitleMenuItems(lektorHideSubtitles, onSetLektorHideSubtitles))

    if (selectedVoice.isOnline) {
        add(
            lektorEdgeOptionsNestedMenu(
                currentRate = lektorEdgeRate,
                currentPitch = lektorEdgePitch,
                currentVolume = lektorEdgeVolume,
                onSelectRate = onChangeLektorEdgeRate,
                onSelectPitch = onChangeLektorEdgePitch,
                onSelectVolume = onChangeLektorEdgeVolume,
                onTestVoice = onTestVoice,
            ),
        )
    }

    add(lektorTimingNestedMenu(lektorTimingOffsetMs, onChangeLektorTimingOffset))
    add(lektorDuckingNestedMenu(lektorDuckingVolume, onChangeLektorDuckingVolume))
}

fun buildLektorNestedMenu(
    isLektorEnabled: Boolean,
    lektorVoiceId: String,
    downloadedVoices: Set<String>,
    downloadProgress: Float?,
    isExtracting: Boolean,
    systemVoices: SystemVoiceList,
    lektorHideSubtitles: Boolean,
    lektorTimingOffsetMs: Int,
    lektorDuckingVolume: Float,
    lektorEdgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
    lektorEdgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
    lektorEdgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    onToggleLektor: (Boolean) -> Unit,
    onSelectLektorVoice: (String) -> Unit,
    onDownloadLektorModel: (String) -> Unit,
    onDeleteLektorModel: (String) -> Unit,
    onPreviewSystemVoice: (String?) -> Unit,
    onRefreshSystemVoices: () -> Unit,
    onSetLektorHideSubtitles: (Boolean) -> Unit,
    onChangeLektorTimingOffset: (Int) -> Unit,
    onChangeLektorDuckingVolume: (Float) -> Unit,
    onChangeLektorEdgeRate: (String) -> Unit = {},
    onChangeLektorEdgePitch: (String) -> Unit = {},
    onChangeLektorEdgeVolume: (String) -> Unit = {},
    onTestVoice: () -> Unit = {},
): FilmanOverlayMenuItem.NestedMenu = FilmanOverlayMenuItem.NestedMenu(
    id = "menu_lektor",
    label = TextValue.StringResource(R.string.player_lektor_title),
    value = TextValue.StringResource(
        if (isLektorEnabled) {
            R.string.player_lektor_enabled
        } else {
            R.string.player_lektor_disabled
        },
    ),
    items = buildLektorMenuItems(
        isLektorEnabled = isLektorEnabled,
        lektorVoiceId = lektorVoiceId,
        downloadedVoices = downloadedVoices,
        downloadProgress = downloadProgress,
        isExtracting = isExtracting,
        systemVoices = systemVoices,
        lektorHideSubtitles = lektorHideSubtitles,
        lektorTimingOffsetMs = lektorTimingOffsetMs,
        lektorDuckingVolume = lektorDuckingVolume,
        lektorEdgeRate = lektorEdgeRate,
        lektorEdgePitch = lektorEdgePitch,
        lektorEdgeVolume = lektorEdgeVolume,
        onToggleLektor = onToggleLektor,
        onSelectLektorVoice = onSelectLektorVoice,
        onDownloadLektorModel = onDownloadLektorModel,
        onDeleteLektorModel = onDeleteLektorModel,
        onPreviewSystemVoice = onPreviewSystemVoice,
        onRefreshSystemVoices = onRefreshSystemVoices,
        onSetLektorHideSubtitles = onSetLektorHideSubtitles,
        onChangeLektorTimingOffset = onChangeLektorTimingOffset,
        onChangeLektorDuckingVolume = onChangeLektorDuckingVolume,
        onChangeLektorEdgeRate = onChangeLektorEdgeRate,
        onChangeLektorEdgePitch = onChangeLektorEdgePitch,
        onChangeLektorEdgeVolume = onChangeLektorEdgeVolume,
        onTestVoice = onTestVoice,
    ),
)
