package com.pointlessapps.filman.ui.player

import androidx.media3.ui.CaptionStyleCompat
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.core.PlayerConstants
import com.pointlessapps.filman.ui.core.TextValue
import java.net.URL

object PlayerMenuBuilder {
    fun buildOverlayMenuData(
        state: PlayerState,
        videoUrlResolver: VideoUrlResolver,
        initialMenuId: String?,
        onEvent: (FilmanEvent) -> Unit,
    ): OverlayMenuData {
        val alternatives = state.alternativeSources.ifEmpty {
            state.detailedMedia?.let {
                videoUrlResolver.getAlternativeUrls(it)
            } ?: emptyList()
        }

        val overlayItems = mutableListOf<FilmanOverlayMenuItem>()

        overlayItems.add(buildSourcesMenu(state, alternatives, onEvent))

        if (state.audioTracks.size > 1) {
            overlayItems.add(buildAudioMenu(state, onEvent))
        }

        val subtitleMenu = buildSubtitlesMenu(state, alternatives, onEvent)
        if (subtitleMenu != null) {
            overlayItems.add(subtitleMenu)
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

    private fun buildSourcesMenu(
        state: PlayerState,
        alternatives: List<ExtractedVideo>,
        onEvent: (FilmanEvent) -> Unit,
    ): FilmanOverlayMenuItem.NestedMenu {
        val menuItems = mutableListOf<FilmanOverlayMenuItem>()
        val grouped = alternatives.groupBy { it.sourceWebsite }

        grouped.forEach { (website, items) ->
            val label = if (website.isEmpty()) {
                TextValue.StringResource(R.string.player_unknown_source)
            } else if (website.equals("stream_api", ignoreCase = true)) {
                TextValue.DynamicString("Stream API")
            } else {
                TextValue.DynamicString(
                    website.substringBefore(".").replaceFirstChar { it.titlecase() },
                )
            }
            menuItems.add(
                FilmanOverlayMenuItem.Header(
                    id = "header_${website.ifEmpty { "unknown" }}",
                    label = label,
                )
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

    private fun buildAudioMenu(
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

    private fun buildSubtitlesMenu(
        state: PlayerState,
        alternatives: List<ExtractedVideo>,
        onEvent: (FilmanEvent) -> Unit,
    ): FilmanOverlayMenuItem.NestedMenu? {
        val subtitleItems = mutableListOf<FilmanOverlayMenuItem>()
        val currentSource = alternatives.find { it.url == state.videoUrl }

        if (currentSource?.subtitles?.isNotEmpty() == true || state.openSubtitles.isNotEmpty()) {
            subtitleItems.add(
                FilmanOverlayMenuItem.Option(
                    id = "sub_off",
                    label = TextValue.StringResource(R.string.player_subtitles_off),
                    isSelected = state.selectedSubtitleUrl == null,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SelectSubtitle(null))
                    },
                ),
            )
        }

        currentSource?.subtitles?.forEach { subtitle ->
            subtitleItems.add(
                FilmanOverlayMenuItem.Option(
                    id = "sub_${subtitle.url}",
                    label = TextValue.DynamicString(subtitle.label),
                    isSelected = subtitle.url == state.selectedSubtitleUrl,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SelectSubtitle(subtitle.url))
                    },
                ),
            )
        }

        state.openSubtitles.forEach { subtitle ->
            subtitleItems.add(
                FilmanOverlayMenuItem.Option(
                    id = "open_sub_${subtitle.url}",
                    label = TextValue.DynamicString(subtitle.label),
                    isSelected = subtitle.url == state.selectedSubtitleUrl,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SelectSubtitle(subtitle.url))
                    },
                ),
            )
        }

        val candidatesList = state.subtitleCandidates.ifEmpty {
            state.openSubtitleCandidates.map { candidate ->
                SubtitleCandidate(
                    id = "open_${candidate.fileId}",
                    name = candidate.fileName,
                    language = candidate.language,
                    release = candidate.release,
                    provider = "OpenSubtitles",
                    downloadCount = candidate.downloadCount,
                    rating = candidate.rating,
                    foreignPartsOnly = candidate.foreignPartsOnly,
                    hearingImpaired = candidate.hearingImpaired,
                    openSubtitleFileId = candidate.fileId,
                )
            }
        }

        if (candidatesList.isNotEmpty()) {
            val candidates = candidatesList.map { candidate ->
                val release = candidate.release?.takeIf { it.isNotBlank() && it != candidate.name && !candidate.name.contains(it) }
                val nameWithProvider = "[${candidate.provider}] ${listOfNotNull(candidate.language.uppercase(), candidate.name, release).joinToString(" · ")}"
                val downloadCount = candidate.downloadCount
                val label = when {
                    candidate.foreignPartsOnly && candidate.hearingImpaired -> R.string.player_subtitle_candidate_forced_hi
                    candidate.foreignPartsOnly -> R.string.player_subtitle_candidate_forced
                    candidate.hearingImpaired -> R.string.player_subtitle_candidate_hi
                    downloadCount != null -> R.string.player_subtitle_candidate
                    else -> null
                }
                val labelText = if (label != null && downloadCount != null) {
                    TextValue.StringResource(label, nameWithProvider, downloadCount)
                } else {
                    TextValue.DynamicString(nameWithProvider)
                }
                FilmanOverlayMenuItem.Option(
                    id = "candidate_${candidate.id}",
                    label = labelText,
                    value = candidate.rating?.let { TextValue.StringResource(R.string.player_subtitle_rating, it.toString()) },
                    isSelected = false,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.DownloadSubtitle(candidate))
                    },
                )
            }
            subtitleItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    id = "menu_subtitle_results",
                    label = TextValue.StringResource(R.string.player_subtitle_results),
                    value = null,
                    items = candidates,
                ),
            )
        }

        val subtitleLangs = listOf("pl", "en", "es", "fr", "de", "it").map { lang ->
            FilmanOverlayMenuItem.Option(
                id = "sub_lang_$lang",
                label = TextValue.DynamicString(lang.uppercase()),
                isSelected = false,
                onClick = {
                    onEvent(BaseEvent.CloseContextMenu)
                    onEvent(PlayerEvent.SearchSubtitles(lang))
                },
            )
        }
        subtitleItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "menu_download_subtitles",
                label = TextValue.StringResource(R.string.player_download_subtitles),
                value = null,
                items = subtitleLangs,
            ),
        )

        if (subtitleItems.isEmpty()) return null

        return FilmanOverlayMenuItem.NestedMenu(
            id = "menu_subtitles",
            label = TextValue.StringResource(R.string.player_subtitles),
            value = null,
            items = subtitleItems,
        )
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildStyleMenu(
        state: PlayerState,
        onEvent: (FilmanEvent) -> Unit,
    ): FilmanOverlayMenuItem.NestedMenu {
        val stylePrefs = state.subtitleStylePreferences

        val fontSizes = listOf(0.03f, 0.0533f, 0.08f, 0.1f, 0.12f, 0.15f)
        val fontSizeItems = fontSizes.map { size ->
            FilmanOverlayMenuItem.Option(
                id = "style_font_size_$size",
                label = TextValue.DynamicString("${intVal(size * 100)}%"),
                isSelected = stylePrefs.fontSizeFraction == size,
                onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(fontSizeFraction = size))) },
            )
        }

        val textColors = listOf(
            Pair(0xFFFFFFFF.toInt(), R.string.player_color_white),
            Pair(0xFFFFFF00.toInt(), R.string.player_color_yellow),
        )
        val textColorItems = textColors.map { (color, stringRes) ->
            FilmanOverlayMenuItem.Option(
                id = "style_color_$color",
                label = TextValue.StringResource(stringRes),
                isSelected = stylePrefs.textColorArgb == color,
                onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(textColorArgb = color))) },
            )
        }

        val backgrounds = listOf(
            Pair(0x00000000.toInt(), R.string.player_color_transparent),
            Pair(0x80000000.toInt(), R.string.player_color_black),
        )
        val backgroundItems = backgrounds.map { (color, stringRes) ->
            FilmanOverlayMenuItem.Option(
                id = "style_bg_$color",
                label = TextValue.StringResource(stringRes),
                isSelected = stylePrefs.backgroundColorArgb == color,
                onClick = {
                    onEvent(
                        PlayerEvent.UpdateSubtitleStyle(
                            stylePrefs.copy(
                                backgroundColorArgb = color,
                            ),
                        ),
                    )
                },
            )
        }

        val edgeTypes = listOf(
            Pair(CaptionStyleCompat.EDGE_TYPE_NONE, R.string.player_edge_none),
            Pair(CaptionStyleCompat.EDGE_TYPE_OUTLINE, R.string.player_edge_outline),
            Pair(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, R.string.player_edge_shadow),
        )
        val edgeTypeItems = edgeTypes.map { (type, stringRes) ->
            FilmanOverlayMenuItem.Option(
                id = "style_edge_$type",
                label = TextValue.StringResource(stringRes),
                isSelected = stylePrefs.edgeType == type,
                onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(edgeType = type))) },
            )
        }

        val verticalOffsets = listOf(0.01f, 0.05f, 0.1f, 0.15f, 0.2f)
        val verticalOffsetItems = verticalOffsets.map { offset ->
            FilmanOverlayMenuItem.Option(
                id = "style_offset_$offset",
                label = TextValue.DynamicString("${intVal(offset * 100)}%"),
                isSelected = stylePrefs.verticalPaddingFraction == offset,
                onClick = {
                    onEvent(
                        PlayerEvent.UpdateSubtitleStyle(
                            stylePrefs.copy(
                                verticalPaddingFraction = offset,
                            ),
                        ),
                    )
                },
            )
        }

        val styleItems = listOf(
            FilmanOverlayMenuItem.NestedMenu(
                id = "style_font_size",
                label = TextValue.StringResource(R.string.player_subtitle_style_font_size),
                value = "${intVal(stylePrefs.fontSizeFraction * 100)}%",
                items = fontSizeItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                id = "style_text_color",
                label = TextValue.StringResource(R.string.player_subtitle_style_text_color),
                value = null,
                items = textColorItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                id = "style_background",
                label = TextValue.StringResource(R.string.player_subtitle_style_background),
                value = null,
                items = backgroundItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                id = "style_edge_type",
                label = TextValue.StringResource(R.string.player_subtitle_style_edge_type),
                value = null,
                items = edgeTypeItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                id = "style_vertical_offset",
                label = TextValue.StringResource(R.string.player_subtitle_style_vertical_offset),
                value = null,
                items = verticalOffsetItems,
            ),
        )

        return FilmanOverlayMenuItem.NestedMenu(
            id = "menu_subtitle_style",
            label = TextValue.StringResource(R.string.player_menu_subtitle_style),
            value = null,
            items = styleItems,
        )
    }

    private fun buildSpeedMenu(
        state: PlayerState,
        onEvent: (FilmanEvent) -> Unit,
    ): FilmanOverlayMenuItem.NestedMenu {
        return FilmanOverlayMenuItem.NestedMenu(
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
    }

    private fun buildAspectRatioMenu(
        state: PlayerState,
        onEvent: (FilmanEvent) -> Unit,
    ): FilmanOverlayMenuItem.NestedMenu {
        return FilmanOverlayMenuItem.NestedMenu(
            id = "menu_aspect_ratio",
            label = TextValue.StringResource(R.string.player_aspect_ratio),
            value = null,
            items = listOf(
                FilmanOverlayMenuItem.Option(
                    id = "aspect_fit",
                    label = TextValue.StringResource(R.string.player_aspect_fit),
                    isSelected = state.aspectRatioMode == PlayerConstants.AspectRatio.FIT,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.FIT))
                    },
                ),
                FilmanOverlayMenuItem.Option(
                    id = "aspect_crop",
                    label = TextValue.StringResource(R.string.player_aspect_crop),
                    isSelected = state.aspectRatioMode == PlayerConstants.AspectRatio.CROP,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.CROP))
                    },
                ),
                FilmanOverlayMenuItem.Option(
                    id = "aspect_stretch",
                    label = TextValue.StringResource(R.string.player_aspect_stretch),
                    isSelected = state.aspectRatioMode == PlayerConstants.AspectRatio.STRETCH,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.STRETCH))
                    },
                ),
            ),
        )
    }

    private fun intVal(f: Float): Int = f.toInt()

    private fun buildLektorMenu(
        state: PlayerState,
        onEvent: (FilmanEvent) -> Unit,
    ): FilmanOverlayMenuItem.NestedMenu = buildLektorNestedMenu(
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
        onTestVoice = { onEvent(PlayerEvent.TestVoice) },
    )
}
