package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.core.TextValue

object PlayerMenuBuilder {
    fun buildOverlayMenuData(
        state: PlayerState,
        videoUrlResolver: VideoUrlResolver,
        initialMenuId: String?,
        onEvent: (TiwiEvent) -> Unit,
    ): OverlayMenuData {
        val alternatives = state.alternativeSources.ifEmpty {
            state.detailedMedia?.let {
                videoUrlResolver.getAlternativeUrls(it)
            } ?: emptyList()
        }

        val overlayItems = mutableListOf<TiwiOverlayMenuItem>()

        val preferredQuality = runCatching {
            (videoUrlResolver.settingsManager.preferredQualityFlow.value as Any?) as? String
        }.getOrNull()?.ifBlank { null } ?: com.pointlessapps.filman.data.local.SettingsConstants.Quality.AUTO

        overlayItems.add(buildSourcesMenu(state, alternatives, preferredQuality, onEvent))

        buildQualityMenu(state, alternatives, onEvent)?.let {
            overlayItems.add(it)
        }

        if (state.audioTracks.size > 1) {
            overlayItems.add(buildAudioMenu(state, onEvent))
        }

        buildSubtitlesMenu(state, alternatives, onEvent)?.let {
            overlayItems.add(it)
        }

        overlayItems.add(buildLektorMenu(state, onEvent))
        overlayItems.add(buildStyleMenu(state, onEvent))
        overlayItems.add(buildSpeedMenu(state, onEvent))
        overlayItems.add(buildAspectRatioMenu(state, onEvent))

        return OverlayMenuData(
            title = TextValue.StringResource(R.string.player_settings),
            items = overlayItems,
            initialMenuId = initialMenuId,
        )
    }

    private fun buildLektorMenu(
        state: PlayerState,
        onEvent: (TiwiEvent) -> Unit,
    ): TiwiOverlayMenuItem.NestedMenu = buildLektorNestedMenu(
        isLektorEnabled = state.isLektorEnabled,
        lektorVoiceId = state.lektorVoiceId,
        downloadedVoices = state.downloadedLektorVoices,
        downloadProgress = state.lektorModelDownloadProgress,
        isExtracting = state.isLektorModelExtracting,
        systemVoices = state.systemVoices,
        lektorHideSubtitles = state.lektorHideSubtitles,
        lektorTimingOffsetMs = state.lektorTimingOffsetMs,
        lektorDuckingVolume = state.lektorDuckingVolume,
        lektorEdgeRate = state.lektorEdgeRate,
        lektorEdgePitch = state.lektorEdgePitch,
        lektorEdgeVolume = state.lektorEdgeVolume,
        lektorEdgeCalmMode = state.lektorEdgeCalmMode,
        onToggleLektor = { onEvent(PlayerEvent.ToggleLektor(it)) },
        onSelectLektorVoice = { onEvent(PlayerEvent.SelectLektorVoice(it)) },
        onDownloadLektorModel = { onEvent(PlayerEvent.DownloadLektorModel(it)) },
        onDeleteLektorModel = { onEvent(PlayerEvent.DeleteLektorModel(it)) },
        onPreviewSystemVoice = { onEvent(PlayerEvent.PreviewSystemVoice(it)) },
        onRefreshSystemVoices = { onEvent(PlayerEvent.RefreshSystemVoices) },
        onSetLektorHideSubtitles = { onEvent(PlayerEvent.SetLektorHideSubtitles(it)) },
        onChangeLektorTimingOffset = { onEvent(PlayerEvent.ChangeLektorTimingOffset(it)) },
        onChangeLektorDuckingVolume = { onEvent(PlayerEvent.ChangeLektorDuckingVolume(it)) },
        onChangeLektorEdgeRate = { onEvent(PlayerEvent.SetLektorEdgeRate(it)) },
        onChangeLektorEdgePitch = { onEvent(PlayerEvent.SetLektorEdgePitch(it)) },
        onChangeLektorEdgeVolume = { onEvent(PlayerEvent.SetLektorEdgeVolume(it)) },
        onChangeLektorEdgeCalmMode = { onEvent(PlayerEvent.SetLektorEdgeCalmMode(it)) },
        onTestVoice = { onEvent(PlayerEvent.TestVoice) },
    )
}
