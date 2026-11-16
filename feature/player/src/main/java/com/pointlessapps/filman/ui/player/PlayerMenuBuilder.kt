package com.pointlessapps.filman.ui.player

import androidx.media3.ui.CaptionStyleCompat
import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.sourceWebsiteConfigName
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
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

        overlayItems.add(buildSourcesMenu(state, alternatives, videoUrlResolver, onEvent))

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
        videoUrlResolver: VideoUrlResolver,
        onEvent: (FilmanEvent) -> Unit,
    ): FilmanOverlayMenuItem.NestedMenu {
        val menuItems = mutableListOf<FilmanOverlayMenuItem>()
        val grouped = alternatives.groupBy { it.sourceWebsite }

        val sourcesPriority = videoUrlResolver.settingsManager.sourcesPriorityFlow.value
        val sortedGrouped = grouped.toList().sortedBy { (website, _) ->
            val websiteConfigName = sourceWebsiteConfigName(website)
            val index = sourcesPriority.indexOfFirst { it.name == websiteConfigName }
            if (index != -1) index else Int.MAX_VALUE
        }

        sortedGrouped.forEach { (website, items) ->
            val label = if (website.isEmpty()) {
                TextValue.StringResource(R.string.player_unknown_source)
            } else if (website.contains("tmdb", ignoreCase = true)) {
                TextValue.DynamicString("TMDB (Direct)")
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

        val openSubtitlesLangs = listOf("pl", "en", "es", "fr", "de", "it").map { lang ->
            FilmanOverlayMenuItem.Option(
                id = "open_sub_lang_$lang",
                label = TextValue.DynamicString(lang.uppercase()),
                isSelected = false,
                onClick = {
                    onEvent(BaseEvent.CloseContextMenu)
                    onEvent(PlayerEvent.SearchOpenSubtitles(lang))
                },
            )
        }
        subtitleItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "menu_download_open_subtitles",
                label = TextValue.StringResource(R.string.player_download_open_subtitles),
                value = null,
                items = openSubtitlesLangs,
            ),
        )

        val wyzieSubtitlesLangs = listOf("pl", "en", "es", "fr", "de", "it").map { lang ->
            FilmanOverlayMenuItem.Option(
                id = "wyzie_sub_lang_$lang",
                label = TextValue.DynamicString(lang.uppercase()),
                isSelected = false,
                onClick = {
                    onEvent(BaseEvent.CloseContextMenu)
                    onEvent(PlayerEvent.SearchWyzieSubtitles(lang))
                },
            )
        }
        subtitleItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "menu_download_wyzie_subs",
                label = TextValue.StringResource(R.string.player_download_wyzie_subs),
                value = null,
                items = wyzieSubtitlesLangs,
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
                label = TextValue.StringResource(stringRes),
                isSelected = stylePrefs.edgeType == type,
                onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(edgeType = type))) },
            )
        }

        val verticalOffsets = listOf(0.01f, 0.05f, 0.1f, 0.15f, 0.2f)
        val verticalOffsetItems = verticalOffsets.map { offset ->
            FilmanOverlayMenuItem.Option(
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
                label = TextValue.StringResource(R.string.player_subtitle_style_font_size),
                value = "${intVal(stylePrefs.fontSizeFraction * 100)}%",
                items = fontSizeItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_subtitle_style_text_color),
                value = null,
                items = textColorItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_subtitle_style_background),
                value = null,
                items = backgroundItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_subtitle_style_edge_type),
                value = null,
                items = edgeTypeItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_subtitle_style_vertical_offset),
                value = null,
                items = verticalOffsetItems,
            ),
        )

        return FilmanOverlayMenuItem.NestedMenu(
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
            label = TextValue.StringResource(R.string.player_playback_speed),
            value = null,
            items = PlayerConstants.PlaybackSpeed.ALL.map { speed ->
                FilmanOverlayMenuItem.Option(
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
            label = TextValue.StringResource(R.string.player_aspect_ratio),
            value = null,
            items = listOf(
                FilmanOverlayMenuItem.Option(
                    label = TextValue.StringResource(R.string.player_aspect_fit),
                    isSelected = state.aspectRatioMode == PlayerConstants.AspectRatio.FIT,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.FIT))
                    },
                ),
                FilmanOverlayMenuItem.Option(
                    label = TextValue.StringResource(R.string.player_aspect_crop),
                    isSelected = state.aspectRatioMode == PlayerConstants.AspectRatio.CROP,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.CROP))
                    },
                ),
                FilmanOverlayMenuItem.Option(
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
    ): FilmanOverlayMenuItem.NestedMenu {
        val lektorItems = mutableListOf<FilmanOverlayMenuItem>()

        // 1. Description in the header
        lektorItems.add(
            FilmanOverlayMenuItem.Header(
                id = "header_lektor_desc",
                label = TextValue.StringResource(R.string.player_lektor_description),
            )
        )

        // 2. Enable / Disable options
        lektorItems.add(
            FilmanOverlayMenuItem.Option(
                id = "lektor_off",
                label = TextValue.StringResource(R.string.player_lektor_disabled),
                isSelected = !state.isLektorEnabled,
                onClick = {
                    onEvent(PlayerEvent.ToggleLektor(false))
                },
            )
        )
        lektorItems.add(
            FilmanOverlayMenuItem.Option(
                id = "lektor_on",
                label = TextValue.StringResource(R.string.player_lektor_enabled),
                isSelected = state.isLektorEnabled,
                onClick = {
                    onEvent(PlayerEvent.ToggleLektor(true))
                },
            )
        )

        // 3. Voice selection header and options (always visible to check status and switch voice)
        lektorItems.add(
            FilmanOverlayMenuItem.Header(
                id = "header_lektor_voice",
                label = TextValue.StringResource(R.string.player_lektor_voice),
            )
        )

        LektorVoice.entries.forEach { voice ->
            val isDownloaded = state.downloadedLektorVoices.contains(voice.id)
            val statusTag = if (isDownloaded) {
                TextValue.StringResource(R.string.player_lektor_model_downloaded_tag)
            } else {
                TextValue.StringResource(R.string.player_lektor_model_not_downloaded_tag)
            }
            lektorItems.add(
                FilmanOverlayMenuItem.Option(
                    id = "lektor_voice_${voice.id}",
                    label = TextValue.DynamicString(voice.displayName),
                    value = statusTag,
                    isSelected = state.lektorVoiceId == voice.id,
                    onClick = {
                        onEvent(PlayerEvent.SelectLektorVoice(voice.id))
                    },
                )
            )
        }

        // 4. Model download and management
        val selectedVoice = LektorVoice.fromId(state.lektorVoiceId)
        val isCurrentModelDownloaded = state.downloadedLektorVoices.contains(selectedVoice.id)

        lektorItems.add(
            FilmanOverlayMenuItem.Header(
                id = "header_lektor_model",
                label = TextValue.StringResource(R.string.player_lektor_model_status),
            )
        )

        when {
            state.lektorModelDownloadProgress != null -> {
                val percent = (state.lektorModelDownloadProgress * 100).toInt()
                lektorItems.add(
                    FilmanOverlayMenuItem.Button(
                        id = "lektor_model_downloading",
                        label = TextValue.StringResource(
                            R.string.player_lektor_model_downloading,
                            listOf(percent),
                        ),
                        value = "$percent%",
                        onClick = {},
                    )
                )
            }
            state.isLektorModelExtracting -> {
                lektorItems.add(
                    FilmanOverlayMenuItem.Button(
                        id = "lektor_model_extracting",
                        label = TextValue.StringResource(R.string.player_lektor_model_extracting),
                        value = null,
                        onClick = {},
                    )
                )
            }
            isCurrentModelDownloaded -> {
                lektorItems.add(
                    FilmanOverlayMenuItem.Button(
                        id = "lektor_model_ready",
                        label = TextValue.StringResource(R.string.player_lektor_model_ready),
                        value = TextValue.StringResource(R.string.player_lektor_model_downloaded_tag),
                        onClick = {},
                    )
                )
                lektorItems.add(
                    FilmanOverlayMenuItem.Button(
                        id = "lektor_model_delete",
                        label = TextValue.StringResource(
                            R.string.player_lektor_delete_model,
                            listOf(selectedVoice.displayName),
                        ),
                        value = null,
                        onClick = {
                            onEvent(PlayerEvent.DeleteLektorModel(selectedVoice.id))
                        },
                    )
                )
            }
            else -> {
                lektorItems.add(
                    FilmanOverlayMenuItem.Button(
                        id = "lektor_model_download",
                        label = TextValue.StringResource(
                            R.string.player_lektor_download_model,
                            listOf(selectedVoice.displayName),
                        ),
                        value = TextValue.StringResource(R.string.player_lektor_model_not_downloaded_tag),
                        onClick = {
                            onEvent(PlayerEvent.DownloadLektorModel(selectedVoice.id))
                        },
                    )
                )
            }
        }

        // 5. Ducking volume header and options
        if (state.isLektorEnabled) {
            lektorItems.add(
                FilmanOverlayMenuItem.Header(
                    id = "header_lektor_ducking",
                    label = TextValue.StringResource(R.string.player_lektor_ducking),
                )
            )

            listOf(
                0.22f to R.string.player_lektor_ducking_medium,
                0.35f to R.string.player_lektor_ducking_low,
                0.12f to R.string.player_lektor_ducking_high,
            ).forEach { (volume, labelRes) ->
                lektorItems.add(
                    FilmanOverlayMenuItem.Option(
                        id = "lektor_ducking_$volume",
                        label = TextValue.StringResource(labelRes),
                        isSelected = kotlin.math.abs(state.lektorDuckingVolume - volume) < 0.05f,
                        onClick = {
                            onEvent(PlayerEvent.ChangeLektorDuckingVolume(volume))
                        },
                    )
                )
            }
        }

        return FilmanOverlayMenuItem.NestedMenu(
            id = "menu_lektor",
            label = TextValue.StringResource(R.string.player_lektor_title),
            value = TextValue.StringResource(
                if (state.isLektorEnabled) {
                    R.string.player_lektor_enabled
                } else {
                    R.string.player_lektor_disabled
                },
            ),
            items = lektorItems,
        )
    }
}
