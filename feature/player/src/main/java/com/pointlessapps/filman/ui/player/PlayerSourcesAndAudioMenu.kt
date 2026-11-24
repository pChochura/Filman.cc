package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.VideoQualityUtils
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.PlayerConstants
import com.pointlessapps.filman.ui.core.TextValue
import java.net.URL

private data class SourceGroupKey(
    val serverName: String,
    val version: String,
)

internal fun buildSourcesMenu(
    state: PlayerState,
    alternatives: List<ExtractedVideo>,
    preferredQuality: String = SettingsConstants.Quality.AUTO,
    onEvent: (TiwiEvent) -> Unit,
): TiwiOverlayMenuItem.NestedMenu {
    val menuItems = mutableListOf<TiwiOverlayMenuItem>()
    val grouped = alternatives.groupBy { it.sourceWebsite }

    grouped.forEach { (website, items) ->
        val label = when {
            website.isEmpty() -> TextValue.StringResource(R.string.player_unknown_source)
            website.equals("stream_api", ignoreCase = true) -> TextValue.StringResource(R.string.player_source_stream_api)
            else -> TextValue.DynamicString(
                website.substringBefore(".").replaceFirstChar { it.titlecase() },
            )
        }
        menuItems.add(
            TiwiOverlayMenuItem.Header(
                id = "header_${website.ifEmpty { "unknown" }}",
                label = label,
            ),
        )

        val sourceGroups = items.groupBy { video ->
            val serverName = video.serverName.ifEmpty {
                runCatching { URL(video.url).host }.getOrNull().orEmpty()
            }.ifEmpty { video.sourceWebsite }
            SourceGroupKey(serverName.trim().lowercase(), video.version.trim().lowercase())
        }

        sourceGroups.forEach { (_, groupVideos) ->
            val representative = groupVideos.first()
            val serverName = representative.serverName.ifEmpty {
                runCatching { URL(representative.url).host }.getOrNull().orEmpty()
            }.ifEmpty { representative.sourceWebsite }
            val tags = listOf(
                serverName,
                representative.version,
            ).filter { it.isNotBlank() }

            val labelText = if (tags.isNotEmpty()) {
                tags.joinToString(" • ")
            } else {
                website.ifEmpty { "Source" }
            }

            val isSelected = groupVideos.any { it.url == state.videoUrl }
            val optionId = "source_${representative.sourceWebsite}_${serverName}_${representative.version}_${representative.url}"

            menuItems.add(
                TiwiOverlayMenuItem.Option(
                    id = optionId,
                    label = TextValue.DynamicString(labelText),
                    isSelected = isSelected,
                    onClick = {
                        val chosen = VideoQualityUtils.selectVideoMatchingQuality(groupVideos, preferredQuality)
                            ?: representative
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.ChangeVideoSource(chosen))
                    },
                ),
            )
        }
    }

    if (state.isFetchingMoreSources) {
        menuItems.add(
            TiwiOverlayMenuItem.Button(
                id = "sources_loading_indicator",
                label = TextValue.StringResource(R.string.player_searching_sources),
                isLoading = true,
                onClick = {},
            ),
        )
    } else if (menuItems.isEmpty()) {
        menuItems.add(
            TiwiOverlayMenuItem.Header(
                id = "no_sources",
                label = TextValue.StringResource(R.string.common_no_alternative_sources),
            ),
        )
    }

    return TiwiOverlayMenuItem.NestedMenu(
        id = PlayerConstants.MENU_SOURCES_ID,
        label = TextValue.StringResource(R.string.player_video_source),
        value = null,
        items = menuItems,
    )
}

internal fun buildQualityMenu(
    state: PlayerState,
    alternatives: List<ExtractedVideo>,
    onEvent: (TiwiEvent) -> Unit,
): TiwiOverlayMenuItem.NestedMenu? {
    val currentUrl = state.videoUrl ?: return null
    val currentVideo = alternatives.find { it.url == currentUrl }

    val currentServer = currentVideo?.serverName?.ifEmpty {
        runCatching { URL(currentVideo.url).host }.getOrNull().orEmpty()
    }?.ifEmpty { currentVideo.sourceWebsite }.orEmpty()

    val currentVersion = currentVideo?.version.orEmpty()
    val currentWebsite = currentVideo?.sourceWebsite.orEmpty()

    val groupVideos = if (currentVideo != null) {
        alternatives.filter { video ->
            val server = video.serverName.ifEmpty {
                runCatching { URL(video.url).host }.getOrNull().orEmpty()
            }.ifEmpty { video.sourceWebsite }

            video.sourceWebsite.equals(currentWebsite, ignoreCase = true) &&
                server.equals(currentServer, ignoreCase = true) &&
                video.version.equals(currentVersion, ignoreCase = true)
        }
    } else {
        emptyList()
    }

    val distinctQualities = groupVideos
        .filter { it.quality.isNotBlank() }
        .distinctBy { it.quality.lowercase().trim() }
        .sortedByDescending { VideoQualityUtils.parseQualityResolution(it.quality) }

    if (distinctQualities.isEmpty() && currentVideo?.quality.isNullOrBlank()) {
        return null
    }

    val currentQuality = currentVideo?.quality?.takeIf { it.isNotBlank() }

    val qualityItems = if (distinctQualities.isNotEmpty()) {
        distinctQualities.map { video ->
            val isSelected = video.url == currentUrl ||
                video.quality.equals(currentQuality, ignoreCase = true)
            TiwiOverlayMenuItem.Option(
                id = "quality_${video.quality.lowercase().trim()}",
                label = TextValue.DynamicString(video.quality),
                isSelected = isSelected,
                onClick = {
                    onEvent(BaseEvent.CloseContextMenu)
                    onEvent(PlayerEvent.ChangeVideoSource(video))
                },
            )
        }
    } else if (currentQuality != null) {
        listOf(
            TiwiOverlayMenuItem.Option(
                id = "quality_${currentQuality.lowercase().trim()}",
                label = TextValue.DynamicString(currentQuality),
                isSelected = true,
                onClick = {
                    onEvent(BaseEvent.CloseContextMenu)
                },
            ),
        )
    } else {
        emptyList()
    }

    if (qualityItems.isEmpty()) return null

    return TiwiOverlayMenuItem.NestedMenu(
        id = PlayerConstants.MENU_QUALITY_ID,
        label = TextValue.StringResource(R.string.player_video_quality),
        value = currentQuality?.let { TextValue.DynamicString(it) },
        items = qualityItems,
    )
}

internal fun buildAudioMenu(
    state: PlayerState,
    onEvent: (TiwiEvent) -> Unit,
): TiwiOverlayMenuItem.NestedMenu {
    val audioItems = state.audioTracks.map { track ->
        TiwiOverlayMenuItem.Option(
            id = "audio_${track.id}",
            label = TextValue.DynamicString(track.label),
            isSelected = track.id == state.selectedAudioTrackId ||
                (state.selectedAudioTrackId == null && track.isSelected),
            onClick = {
                onEvent(BaseEvent.CloseContextMenu)
                onEvent(PlayerEvent.SelectAudioTrack(track.id))
            },
        )
    }
    return TiwiOverlayMenuItem.NestedMenu(
        id = "menu_audio",
        label = TextValue.StringResource(R.string.player_audio_track),
        value = null,
        items = audioItems,
    )
}

internal fun buildSpeedMenu(
    state: PlayerState,
    onEvent: (TiwiEvent) -> Unit,
): TiwiOverlayMenuItem.NestedMenu = TiwiOverlayMenuItem.NestedMenu(
    id = "menu_speed",
    label = TextValue.StringResource(R.string.player_playback_speed),
    value = null,
    items = PlayerConstants.PlaybackSpeed.ALL.map { speed ->
        TiwiOverlayMenuItem.Option(
            id = "speed_$speed",
            label = TextValue.StringResource(
                R.string.player_speed_format,
                listOf(speed.toString()),
            ),
            isSelected = state.playbackSpeed == speed,
            onClick = {
                onEvent(BaseEvent.CloseContextMenu)
                onEvent(PlayerEvent.ChangePlaybackSpeed(speed))
            },
        )
    },
)

internal fun buildAspectRatioMenu(
    state: PlayerState,
    onEvent: (TiwiEvent) -> Unit,
): TiwiOverlayMenuItem.NestedMenu = TiwiOverlayMenuItem.NestedMenu(
    id = "menu_aspect_ratio",
    label = TextValue.StringResource(R.string.player_aspect_ratio),
    value = null,
    items = listOf(
        PlayerConstants.AspectRatio.FIT to ("aspect_fit" to R.string.player_aspect_fit),
        PlayerConstants.AspectRatio.CROP to ("aspect_crop" to R.string.player_aspect_crop),
        PlayerConstants.AspectRatio.STRETCH to ("aspect_stretch" to R.string.player_aspect_stretch),
    ).map { (mode, idAndLabel) ->
        val (id, labelRes) = idAndLabel
        TiwiOverlayMenuItem.Option(
            id = id,
            label = TextValue.StringResource(labelRes),
            isSelected = state.aspectRatioMode == mode,
            onClick = {
                onEvent(BaseEvent.CloseContextMenu)
                onEvent(PlayerEvent.ChangeAspectRatio(mode))
            },
        )
    },
)
