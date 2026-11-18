package com.pointlessapps.filman.ui.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

internal fun buildSubtitlesMenu(
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
            val release = candidate.release?.takeIf {
                it.isNotBlank() && it != candidate.name && !candidate.name.contains(it)
            }
            val nameWithProvider = "[${candidate.provider}] ${
                listOfNotNull(candidate.language.uppercase(), candidate.name, release).joinToString(" · ")
            }"
            val downloadCount = candidate.downloadCount
            val labelRes = when {
                candidate.foreignPartsOnly && candidate.hearingImpaired -> R.string.player_subtitle_candidate_forced_hi
                candidate.foreignPartsOnly -> R.string.player_subtitle_candidate_forced
                candidate.hearingImpaired -> R.string.player_subtitle_candidate_hi
                downloadCount != null -> R.string.player_subtitle_candidate
                else -> null
            }
            val labelText = if (labelRes != null && downloadCount != null) {
                TextValue.StringResource(labelRes, nameWithProvider, downloadCount)
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

@OptIn(UnstableApi::class)
internal fun buildStyleMenu(
    state: PlayerState,
    onEvent: (FilmanEvent) -> Unit,
): FilmanOverlayMenuItem.NestedMenu {
    val stylePrefs = state.subtitleStylePreferences

    val fontSizeItems = listOf(0.03f, 0.0533f, 0.08f, 0.1f, 0.12f, 0.15f).map { size ->
        FilmanOverlayMenuItem.Option(
            id = "style_font_size_$size",
            label = TextValue.DynamicString("${(size * 100).toInt()}%"),
            isSelected = stylePrefs.fontSizeFraction == size,
            onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(fontSizeFraction = size))) },
        )
    }

    val textColorItems = listOf(
        0xFFFFFFFF.toInt() to R.string.player_color_white,
        0xFFFFFF00.toInt() to R.string.player_color_yellow,
    ).map { (color, stringRes) ->
        FilmanOverlayMenuItem.Option(
            id = "style_color_$color",
            label = TextValue.StringResource(stringRes),
            isSelected = stylePrefs.textColorArgb == color,
            onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(textColorArgb = color))) },
        )
    }

    val backgroundItems = listOf(
        0x00000000.toInt() to R.string.player_color_transparent,
        0x80000000.toInt() to R.string.player_color_black,
    ).map { (color, stringRes) ->
        FilmanOverlayMenuItem.Option(
            id = "style_bg_$color",
            label = TextValue.StringResource(stringRes),
            isSelected = stylePrefs.backgroundColorArgb == color,
            onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(backgroundColorArgb = color))) },
        )
    }

    val edgeTypeItems = listOf(
        CaptionStyleCompat.EDGE_TYPE_NONE to R.string.player_edge_none,
        CaptionStyleCompat.EDGE_TYPE_OUTLINE to R.string.player_edge_outline,
        CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to R.string.player_edge_shadow,
    ).map { (type, stringRes) ->
        FilmanOverlayMenuItem.Option(
            id = "style_edge_$type",
            label = TextValue.StringResource(stringRes),
            isSelected = stylePrefs.edgeType == type,
            onClick = { onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(edgeType = type))) },
        )
    }

    val verticalOffsetItems = listOf(0.01f, 0.05f, 0.1f, 0.15f, 0.2f).map { offset ->
        FilmanOverlayMenuItem.Option(
            id = "style_offset_$offset",
            label = TextValue.DynamicString("${(offset * 100).toInt()}%"),
            isSelected = stylePrefs.verticalPaddingFraction == offset,
            onClick = {
                onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(verticalPaddingFraction = offset)))
            },
        )
    }

    val styleItems = listOf(
        FilmanOverlayMenuItem.NestedMenu(
            id = "style_font_size",
            label = TextValue.StringResource(R.string.player_subtitle_style_font_size),
            value = "${(stylePrefs.fontSizeFraction * 100).toInt()}%",
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
