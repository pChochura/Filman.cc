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
): List<FilmanOverlayMenuItem> = buildList {
    // 1. Description header
    add(
        FilmanOverlayMenuItem.Header(
            id = "header_lektor_desc",
            label = TextValue.StringResource(R.string.player_lektor_description),
        ),
    )

    // 2. Enable / disable options
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

    // 3. Voice selection header
    add(
        FilmanOverlayMenuItem.Header(
            id = "header_lektor_voice",
            label = TextValue.StringResource(R.string.player_lektor_voice),
        ),
    )

    val selectedVoice = LektorVoice.fromId(lektorVoiceId)

    // Online models
    val onlineVoices = LektorVoice.entries.filter { it.isOnline }
    if (onlineVoices.size == 1) {
        val voice = onlineVoices.first()
        add(
            FilmanOverlayMenuItem.Option(
                id = "lektor_voice_${voice.id}",
                label = TextValue.DynamicString(voice.displayName),
                isSelected = lektorVoiceId == voice.id,
                onClick = { onSelectLektorVoice(voice.id) },
            ),
        )
    } else if (onlineVoices.size > 1) {
        val onlineItems = buildList {
            add(
                FilmanOverlayMenuItem.Header(
                    id = "lektor_online_notice",
                    label = TextValue.StringResource(R.string.player_lektor_online_notice),
                ),
            )
            onlineVoices.forEach { voice ->
                add(
                    FilmanOverlayMenuItem.Option(
                        id = "lektor_voice_${voice.id}",
                        label = TextValue.DynamicString(voice.displayName),
                        isSelected = lektorVoiceId == voice.id,
                        onClick = { onSelectLektorVoice(voice.id) },
                    ),
                )
            }
            add(
                FilmanOverlayMenuItem.Header(
                    id = "header_lektor_edge_options",
                    label = TextValue.StringResource(R.string.player_lektor_edge_options),
                ),
            )
            add(lektorEdgeRateNestedMenu(lektorEdgeRate, onChangeLektorEdgeRate))
            add(lektorEdgePitchNestedMenu(lektorEdgePitch, onChangeLektorEdgePitch))
            add(lektorEdgeVolumeNestedMenu(lektorEdgeVolume, onChangeLektorEdgeVolume))
        }
        val onlineValue = onlineVoices.find { it.id == lektorVoiceId }?.let {
            TextValue.DynamicString(it.displayName)
        }
        add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "menu_lektor_online_models",
                label = TextValue.StringResource(R.string.player_lektor_online_models),
                value = onlineValue,
                items = onlineItems,
            ),
        )
    }

    // System model
    val systemVoiceChoicesCount = systemVoices.names.size
    if (systemVoiceChoicesCount <= 1) {
        // Keep flat if there's only one choice
        add(
            FilmanOverlayMenuItem.Option(
                id = "lektor_voice_${LektorVoice.SYSTEM.id}",
                label = TextValue.StringResource(R.string.player_lektor_system_voice),
                isSelected = selectedVoice.isSystem,
                onClick = { onSelectLektorVoice(LektorVoice.SYSTEM.id) },
            ),
        )
        if (selectedVoice.isSystem) {
            addAll(
                systemVoiceMenuItems(
                    voices = systemVoices,
                    selectedVoiceId = lektorVoiceId,
                    onSelect = onSelectLektorVoice,
                    onPreview = onPreviewSystemVoice,
                    onRefresh = onRefreshSystemVoices,
                ),
            )
        }
    } else {
        // Multiple system voices available -> hide in nested menu
        val systemVoiceItems = systemVoiceMenuItems(
            voices = systemVoices,
            selectedVoiceId = lektorVoiceId,
            onSelect = onSelectLektorVoice,
            onPreview = onPreviewSystemVoice,
            onRefresh = onRefreshSystemVoices,
        )
        val selectedSystemName = LektorVoice.systemVoiceName(lektorVoiceId)
        val systemValue = if (selectedVoice.isSystem) {
            selectedSystemName?.let { TextValue.DynamicString(it) }
                ?: TextValue.StringResource(R.string.player_lektor_system_voice)
        } else {
            null
        }
        add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "menu_lektor_system_voices",
                label = TextValue.StringResource(R.string.player_lektor_system_voice),
                value = systemValue,
                items = systemVoiceItems,
            ),
        )
    }

    // Local models
    val localVoices = LektorVoice.entries.filter { it.requiresModel }
    if (localVoices.size == 1) {
        val voice = localVoices.first()
        val isDownloaded = downloadedVoices.contains(voice.id)
        val statusTag = if (isDownloaded) {
            TextValue.StringResource(R.string.player_lektor_model_downloaded_tag)
        } else {
            TextValue.StringResource(R.string.player_lektor_model_not_downloaded_tag)
        }
        add(
            FilmanOverlayMenuItem.Option(
                id = "lektor_voice_${voice.id}",
                label = TextValue.DynamicString(voice.displayName),
                value = statusTag,
                isSelected = lektorVoiceId == voice.id,
                onClick = { onSelectLektorVoice(voice.id) },
            ),
        )
        if (selectedVoice.id == voice.id) {
            addAll(
                buildModelManagementItems(
                    selectedVoice = voice,
                    isDownloaded = isDownloaded,
                    downloadProgress = downloadProgress,
                    isExtracting = isExtracting,
                    onDownloadModel = onDownloadLektorModel,
                    onDeleteModel = onDeleteLektorModel,
                ),
            )
        }
    } else if (localVoices.size > 1) {
        val localItems = buildList {
            add(
                FilmanOverlayMenuItem.Header(
                    id = "header_lektor_local_voices",
                    label = TextValue.StringResource(R.string.player_lektor_local_models),
                ),
            )
            localVoices.forEach { voice ->
                val isDownloaded = downloadedVoices.contains(voice.id)
                val statusTag = if (isDownloaded) {
                    TextValue.StringResource(R.string.player_lektor_model_downloaded_tag)
                } else {
                    TextValue.StringResource(R.string.player_lektor_model_not_downloaded_tag)
                }
                add(
                    FilmanOverlayMenuItem.Option(
                        id = "lektor_voice_${voice.id}",
                        label = TextValue.DynamicString(voice.displayName),
                        value = statusTag,
                        isSelected = lektorVoiceId == voice.id,
                        onClick = { onSelectLektorVoice(voice.id) },
                    ),
                )
            }
            if (selectedVoice.requiresModel) {
                val isCurrentDownloaded = downloadedVoices.contains(selectedVoice.id)
                addAll(
                    buildModelManagementItems(
                        selectedVoice = selectedVoice,
                        isDownloaded = isCurrentDownloaded,
                        downloadProgress = downloadProgress,
                        isExtracting = isExtracting,
                        onDownloadModel = onDownloadLektorModel,
                        onDeleteModel = onDeleteLektorModel,
                    ),
                )
            }
        }
        val localValue = localVoices.find { it.id == lektorVoiceId }?.let {
            TextValue.DynamicString(it.displayName)
        }
        add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "menu_lektor_local_models",
                label = TextValue.StringResource(R.string.player_lektor_local_models),
                value = localValue,
                items = localItems,
            ),
        )
    }

    // 4. Subtitles during voice-over
    addAll(lektorSubtitleMenuItems(lektorHideSubtitles, onSetLektorHideSubtitles))

    // 5. Edge-TTS voice options when selected voice is online
    if (selectedVoice.isOnline) {
        add(
            lektorEdgeOptionsNestedMenu(
                currentRate = lektorEdgeRate,
                currentPitch = lektorEdgePitch,
                currentVolume = lektorEdgeVolume,
                onSelectRate = onChangeLektorEdgeRate,
                onSelectPitch = onChangeLektorEdgePitch,
                onSelectVolume = onChangeLektorEdgeVolume,
            ),
        )
    }

    // 6. Timing offset nested menu
    add(lektorTimingNestedMenu(lektorTimingOffsetMs, onChangeLektorTimingOffset))

    // 7. Ducking nested menu
    add(lektorDuckingNestedMenu(lektorDuckingVolume, onChangeLektorDuckingVolume))
}

private fun buildModelManagementItems(
    selectedVoice: LektorVoice,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    isExtracting: Boolean,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
): List<FilmanOverlayMenuItem> = buildList {
    when {
        downloadProgress != null -> {
            val percent = (downloadProgress * 100).toInt()
            add(
                FilmanOverlayMenuItem.Button(
                    id = "lektor_model_downloading",
                    label = TextValue.StringResource(
                        R.string.player_lektor_model_downloading,
                        listOf(percent),
                    ),
                    value = "$percent%",
                    onClick = {},
                ),
            )
        }
        isExtracting -> {
            add(
                FilmanOverlayMenuItem.Button(
                    id = "lektor_model_extracting",
                    label = TextValue.StringResource(R.string.player_lektor_model_extracting),
                    value = null,
                    onClick = {},
                ),
            )
        }
        isDownloaded -> {
            add(
                FilmanOverlayMenuItem.Button(
                    id = "lektor_model_delete",
                    label = TextValue.StringResource(
                        R.string.player_lektor_delete_model,
                        listOf(selectedVoice.displayName),
                    ),
                    value = null,
                    onClick = { onDeleteModel(selectedVoice.id) },
                ),
            )
        }
        else -> {
            add(
                FilmanOverlayMenuItem.Button(
                    id = "lektor_model_download",
                    label = TextValue.StringResource(
                        R.string.player_lektor_download_model,
                        listOf(selectedVoice.displayName),
                    ),
                    value = TextValue.StringResource(R.string.player_lektor_model_not_downloaded_tag),
                    onClick = { onDownloadModel(selectedVoice.id) },
                ),
            )
        }
    }
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
    ),
)
