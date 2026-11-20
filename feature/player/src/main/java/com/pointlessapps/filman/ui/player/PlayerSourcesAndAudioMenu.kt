package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.PlayerConstants
import com.pointlessapps.filman.ui.core.TextValue
import java.net.URL

internal fun buildSourcesMenu(
    state: PlayerState,
    alternatives: List<ExtractedVideo>,
    onEvent: (FilmanEvent) -> Unit,
): FilmanOverlayMenuItem.NestedMenu {
    val menuItems = mutableListOf<FilmanOverlayMenuItem>()
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
            FilmanOverlayMenuItem.Header(
                id = "header_${website.ifEmpty { "unknown" }}",
                label = label,
            ),
        )

        items.forEach { extracted ->
            val serverName = extracted.serverName.ifEmpty {
                runCatching { URL(extracted.url).host }.getOrNull().orEmpty()
            }.ifEmpty { extracted.sourceWebsite }
            val tags = listOf(
                serverName,
                extracted.version,
                extracted.quality,
            ).filter { it.isNotBlank() }

            menuItems.add(
                FilmanOverlayMenuItem.Option(
                    id = "source_${extracted.sourceWebsite}_${extracted.serverName}_${extracted.quality}_${extracted.url}",
                    label = TextValue.DynamicString(tags.joinToString(" • ")),
                    isSelected = extracted.url == state.videoUrl,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.ChangeVideoSource(extracted))
                    },
                ),
            )
        }
    }

    return FilmanOverlayMenuItem.NestedMenu(
        id = PlayerConstants.MENU_SOURCES_ID,
        label = TextValue.StringResource(R.string.player_video_source),
        value = null,
        items = menuItems,
    )
}

internal fun buildAudioMenu(
    state: PlayerState,
    onEvent: (FilmanEvent) -> Unit,
): FilmanOverlayMenuItem.NestedMenu {
    val audioItems = state.audioTracks.map { track ->
        FilmanOverlayMenuItem.Option(
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
    return FilmanOverlayMenuItem.NestedMenu(
        id = "menu_audio",
        label = TextValue.StringResource(R.string.player_audio_track),
        value = null,
        items = audioItems,
    )
}

internal fun buildSpeedMenu(
    state: PlayerState,
    onEvent: (FilmanEvent) -> Unit,
): FilmanOverlayMenuItem.NestedMenu = FilmanOverlayMenuItem.NestedMenu(
    id = "menu_speed",
    label = TextValue.StringResource(R.string.player_playback_speed),
    value = null,
    items = PlayerConstants.PlaybackSpeed.ALL.map { speed ->
        FilmanOverlayMenuItem.Option(
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
    onEvent: (FilmanEvent) -> Unit,
): FilmanOverlayMenuItem.NestedMenu = FilmanOverlayMenuItem.NestedMenu(
    id = "menu_aspect_ratio",
    label = TextValue.StringResource(R.string.player_aspect_ratio),
    value = null,
    items = listOf(
        PlayerConstants.AspectRatio.FIT to ("aspect_fit" to R.string.player_aspect_fit),
        PlayerConstants.AspectRatio.CROP to ("aspect_crop" to R.string.player_aspect_crop),
        PlayerConstants.AspectRatio.STRETCH to ("aspect_stretch" to R.string.player_aspect_stretch),
    ).map { (mode, idAndLabel) ->
        val (id, labelRes) = idAndLabel
        FilmanOverlayMenuItem.Option(
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
