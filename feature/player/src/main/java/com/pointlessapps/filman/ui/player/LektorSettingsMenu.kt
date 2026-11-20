package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
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
    lektorEdgeCalmMode: Boolean = LektorEdgeVoiceOptions.DEFAULT_CALM_MODE,
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
    onChangeLektorEdgeCalmMode: (Boolean) -> Unit = {},
    onTestVoice: () -> Unit = {},
): List<TiwiOverlayMenuItem> = buildList {
    add(
        TiwiOverlayMenuItem.Header(
            id = "header_lektor_desc",
            label = TextValue.StringResource(R.string.player_lektor_description),
        ),
    )

    add(
        TiwiOverlayMenuItem.Option(
            id = "lektor_off",
            label = TextValue.StringResource(R.string.player_lektor_disabled),
            isSelected = !isLektorEnabled,
            onClick = { onToggleLektor(false) },
        ),
    )
    add(
        TiwiOverlayMenuItem.Option(
            id = "lektor_on",
            label = TextValue.StringResource(R.string.player_lektor_enabled),
            isSelected = isLektorEnabled,
            onClick = { onToggleLektor(true) },
        ),
    )

    add(
        TiwiOverlayMenuItem.Header(
            id = "header_lektor_voice",
            label = TextValue.StringResource(R.string.player_lektor_voice),
        ),
    )

    val selectedVoice = LektorVoice.fromId(lektorVoiceId)

    // Primary choices right at hand: Marek (default online) & Zofia (female online)
    add(
        TiwiOverlayMenuItem.Option(
            id = "lektor_voice_${LektorVoice.EDGE.id}",
            label = TextValue.DynamicString(LektorVoice.EDGE.displayName),
            isSelected = lektorVoiceId == LektorVoice.EDGE.id,
            onClick = { onSelectLektorVoice(LektorVoice.EDGE.id) },
        ),
    )
    add(
        TiwiOverlayMenuItem.Option(
            id = "lektor_voice_${LektorVoice.EDGE_ZOFIA.id}",
            label = TextValue.DynamicString(LektorVoice.EDGE_ZOFIA.displayName),
            isSelected = lektorVoiceId == LektorVoice.EDGE_ZOFIA.id,
            onClick = { onSelectLektorVoice(LektorVoice.EDGE_ZOFIA.id) },
        ),
    )

    // Other voices nested menu
    val otherVoicesItems = buildList {
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
            lektorEdgeCalmMode = lektorEdgeCalmMode,
            onChangeLektorEdgeCalmMode = onChangeLektorEdgeCalmMode,
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
    }

    val isOtherVoiceSelected = lektorVoiceId != LektorVoice.EDGE.id && lektorVoiceId != LektorVoice.EDGE_ZOFIA.id
    val otherVoicesValue = if (isOtherVoiceSelected) {
        TextValue.DynamicString(selectedVoice.displayName)
    } else {
        null
    }

    add(
        TiwiOverlayMenuItem.NestedMenu(
            id = "menu_lektor_other_voices",
            label = TextValue.StringResource(R.string.player_lektor_other_voices),
            value = otherVoicesValue,
            items = otherVoicesItems,
        ),
    )

    addAll(lektorSubtitleMenuItems(lektorHideSubtitles, onSetLektorHideSubtitles))

    add(
        TiwiOverlayMenuItem.Button(
            id = "lektor_test_voice",
            label = TextValue.StringResource(R.string.player_lektor_test_voice),
            value = TextValue.DynamicString(selectedVoice.displayName),
            onClick = { onTestVoice() },
        ),
    )

    // Advanced settings nested menu
    val advancedItems = buildList {
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
                    isCalmMode = lektorEdgeCalmMode,
                    onSelectCalmMode = onChangeLektorEdgeCalmMode,
                ),
            )
        }

        add(lektorTimingNestedMenu(lektorTimingOffsetMs, onChangeLektorTimingOffset))
        add(lektorDuckingNestedMenu(lektorDuckingVolume, onChangeLektorDuckingVolume))
    }

    add(
        TiwiOverlayMenuItem.NestedMenu(
            id = "menu_lektor_advanced",
            label = TextValue.StringResource(R.string.player_lektor_advanced_settings),
            items = advancedItems,
        ),
    )
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
    lektorEdgeCalmMode: Boolean = LektorEdgeVoiceOptions.DEFAULT_CALM_MODE,
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
    onChangeLektorEdgeCalmMode: (Boolean) -> Unit = {},
    onTestVoice: () -> Unit = {},
): TiwiOverlayMenuItem.NestedMenu = TiwiOverlayMenuItem.NestedMenu(
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
        lektorEdgeCalmMode = lektorEdgeCalmMode,
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
        onChangeLektorEdgeCalmMode = onChangeLektorEdgeCalmMode,
        onTestVoice = onTestVoice,
    ),
)
