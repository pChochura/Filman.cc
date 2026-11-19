package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

internal fun MutableList<FilmanOverlayMenuItem>.addOnlineVoiceItems(
    lektorVoiceId: String,
    lektorEdgeRate: String,
    lektorEdgePitch: String,
    lektorEdgeVolume: String,
    onSelectLektorVoice: (String) -> Unit,
    onChangeLektorEdgeRate: (String) -> Unit,
    onChangeLektorEdgePitch: (String) -> Unit,
    onChangeLektorEdgeVolume: (String) -> Unit,
    onTestVoice: () -> Unit,
    lektorEdgeCalmMode: Boolean = LektorEdgeVoiceOptions.DEFAULT_CALM_MODE,
    onChangeLektorEdgeCalmMode: (Boolean) -> Unit = {},
) {
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
                FilmanOverlayMenuItem.Button(
                    id = "lektor_online_test_voice",
                    label = TextValue.StringResource(R.string.player_lektor_test_voice),
                    onClick = { onTestVoice() },
                ),
            )
            add(
                FilmanOverlayMenuItem.Header(
                    id = "header_lektor_edge_options",
                    label = TextValue.StringResource(R.string.player_lektor_edge_options),
                ),
            )
            add(lektorEdgeRateNestedMenu(lektorEdgeRate, onChangeLektorEdgeRate))
            add(lektorEdgePitchNestedMenu(lektorEdgePitch, onChangeLektorEdgePitch))
            add(lektorEdgeVolumeNestedMenu(lektorEdgeVolume, onChangeLektorEdgeVolume))
            add(lektorEdgeCalmModeNestedMenu(lektorEdgeCalmMode, onChangeLektorEdgeCalmMode))
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
}

internal fun MutableList<FilmanOverlayMenuItem>.addSystemVoiceItems(
    lektorVoiceId: String,
    selectedVoice: LektorVoice,
    systemVoices: SystemVoiceList,
    onSelectLektorVoice: (String) -> Unit,
    onPreviewSystemVoice: (String?) -> Unit,
    onRefreshSystemVoices: () -> Unit,
) {
    if (systemVoices.names.size <= 1) {
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
}

internal fun MutableList<FilmanOverlayMenuItem>.addLocalVoiceItems(
    lektorVoiceId: String,
    selectedVoice: LektorVoice,
    downloadedVoices: Set<String>,
    downloadProgress: Float?,
    isExtracting: Boolean,
    onSelectLektorVoice: (String) -> Unit,
    onDownloadLektorModel: (String) -> Unit,
    onDeleteLektorModel: (String) -> Unit,
    onTestVoice: () -> Unit,
) {
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
                    onTestVoice = onTestVoice,
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
                addAll(
                    buildModelManagementItems(
                        selectedVoice = selectedVoice,
                        isDownloaded = downloadedVoices.contains(selectedVoice.id),
                        downloadProgress = downloadProgress,
                        isExtracting = isExtracting,
                        onDownloadModel = onDownloadLektorModel,
                        onDeleteModel = onDeleteLektorModel,
                        onTestVoice = onTestVoice,
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
}

private fun buildModelManagementItems(
    selectedVoice: LektorVoice,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    isExtracting: Boolean,
    onDownloadModel: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onTestVoice: () -> Unit = {},
): List<FilmanOverlayMenuItem> = buildList {
    when {
        downloadProgress != null -> {
            val percent = (downloadProgress * 100).toInt()
            add(
                FilmanOverlayMenuItem.Button(
                    id = "lektor_model_downloading",
                    label = TextValue.StringResource(R.string.player_lektor_model_downloading, listOf(percent)),
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
                    id = "lektor_local_test_voice",
                    label = TextValue.StringResource(R.string.player_lektor_test_voice),
                    onClick = { onTestVoice() },
                ),
            )
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
