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
import java.util.Locale

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

    if (state.selectedSubtitleUrl != null) {
        subtitleItems.add(0, buildSubtitleSyncMenu(state, onEvent))
    }

    if (subtitleItems.isEmpty()) return null

    return FilmanOverlayMenuItem.NestedMenu(
        id = "menu_subtitles",
        label = TextValue.StringResource(R.string.player_subtitles),
        value = null,
        items = subtitleItems,
    )
}

internal fun buildSubtitleSyncMenu(
    state: PlayerState,
    onEvent: (FilmanEvent) -> Unit,
): FilmanOverlayMenuItem.NestedMenu {
    val items = mutableListOf<FilmanOverlayMenuItem>()

    // 1. One-click Auto Align
    items.add(
        FilmanOverlayMenuItem.Button(
            id = "btn_auto_align",
            label = TextValue.StringResource(
                if (state.isAligningSubtitles) {
                    R.string.player_subtitle_auto_align_running
                } else {
                    R.string.player_subtitle_auto_align_button
                },
            ),
            value = TextValue.StringResource(R.string.player_subtitle_auto_align_desc),
            onClick = {
                if (!state.isAligningSubtitles) {
                    onEvent(PlayerEvent.AutoAlignCurrentSubtitles)
                }
            },
        ),
    )

    // Optional: Align with specific track if other tracks exist
    val otherSubs = (state.subtitles + state.openSubtitles)
        .filter { it.url != state.selectedSubtitleUrl }
        .distinctBy { it.url }

    if (otherSubs.isNotEmpty()) {
        val specificAlignItems = otherSubs.map { sub ->
            FilmanOverlayMenuItem.Option(
                id = "align_with_${sub.url.hashCode()}",
                label = TextValue.DynamicString(sub.label),
                isSelected = false,
                onClick = { onEvent(PlayerEvent.AutoAlignSubtitles(sub.url)) },
            )
        }
        items.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "menu_align_specific",
                label = TextValue.StringResource(R.string.player_subtitle_auto_align),
                value = null,
                items = specificAlignItems,
            ),
        )
    }

    // 2. Quick Shift Section (Nudges)
    items.add(
        FilmanOverlayMenuItem.Header(
            id = "header_quick_shift",
            label = TextValue.StringResource(R.string.player_subtitle_quick_shift),
        ),
    )
    items.add(
        FilmanOverlayMenuItem.Button(
            id = "btn_shift_earlier_500",
            label = TextValue.StringResource(R.string.player_subtitle_shift_earlier_by, "0.5"),
            value = null,
            onClick = { onEvent(PlayerEvent.AdjustSubtitleOffset(-500)) },
        ),
    )
    items.add(
        FilmanOverlayMenuItem.Button(
            id = "btn_shift_later_500",
            label = TextValue.StringResource(R.string.player_subtitle_shift_later_by, "0.5"),
            value = null,
            onClick = { onEvent(PlayerEvent.AdjustSubtitleOffset(500)) },
        ),
    )
    items.add(
        FilmanOverlayMenuItem.Button(
            id = "btn_reset_sync",
            label = TextValue.StringResource(R.string.player_subtitle_reset_sync),
            value = null,
            onClick = {
                onEvent(PlayerEvent.SetSubtitleOffset(0))
                onEvent(PlayerEvent.SetSubtitleFpsScale(1.0))
            },
        ),
    )

    // 3. Exact Timings
    val currentSec = String.format(Locale.US, "%.1f", state.subtitleOffsetMs / 1000.0)
    items.add(
        FilmanOverlayMenuItem.Header(
            id = "header_exact_shift",
            label = TextValue.StringResource(
                R.string.player_subtitle_current_offset,
                if (state.subtitleOffsetMs > 0) "+$currentSec" else currentSec,
            ),
        ),
    )

    val standardOffsets = listOf(
        -5000 to "5.0",
        -2500 to "2.5",
        -1000 to "1.0",
        -500 to "0.5",
        -200 to "0.2",
        0 to "0.0",
        200 to "0.2",
        500 to "0.5",
        1000 to "1.0",
        2500 to "2.5",
        5000 to "5.0",
    )

    val hasCurrentInStandard = standardOffsets.any { it.first == state.subtitleOffsetMs }
    if (!hasCurrentInStandard && state.subtitleOffsetMs != 0) {
        val customSec = String.format(Locale.US, "%.2f", state.subtitleOffsetMs / 1000.0)
        val formattedOffset = if (state.subtitleOffsetMs > 0) "+$customSec" else customSec
        items.add(
            FilmanOverlayMenuItem.Option(
                id = "sub_offset_current",
                label = TextValue.StringResource(R.string.player_subtitle_offset_value, formattedOffset),
                isSelected = true,
                onClick = {},
            ),
        )
    }

    standardOffsets.forEach { (offset, secStr) ->
        val labelRes = when {
            offset < 0 -> R.string.player_subtitle_timing_offset_earlier
            offset > 0 -> R.string.player_subtitle_timing_offset_later
            else -> R.string.player_subtitle_timing_on_time
        }
        items.add(
            FilmanOverlayMenuItem.Option(
                id = "sub_offset_$offset",
                label = if (offset != 0) TextValue.StringResource(labelRes, secStr) else TextValue.StringResource(labelRes),
                isSelected = state.subtitleOffsetMs == offset,
                onClick = { onEvent(PlayerEvent.SetSubtitleOffset(offset)) },
            ),
        )
    }

    // 4. Framerate Drift
    val fpsScales = listOf(
        1.0 to R.string.player_subtitle_fps_scale_normal,
        (25.0 / 23.976) to R.string.player_subtitle_fps_25_to_23976,
        (23.976 / 25.0) to R.string.player_subtitle_fps_23976_to_25,
    ).map { (ratio, labelRes) ->
        FilmanOverlayMenuItem.Option(
            id = "sub_fps_$ratio",
            label = TextValue.StringResource(labelRes),
            isSelected = kotlin.math.abs(state.subtitleFpsScale - ratio) < 0.001,
            onClick = { onEvent(PlayerEvent.SetSubtitleFpsScale(ratio)) },
        )
    }

    items.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "menu_subtitle_fps",
            label = TextValue.StringResource(R.string.player_subtitle_fps_scale),
            value = if (state.subtitleFpsScale != 1.0) TextValue.StringResource(R.string.player_subtitle_custom) else null,
            items = fpsScales,
        ),
    )

    val menuValue = if (state.subtitleOffsetMs != 0) {
        val s = String.format(Locale.US, "%.1f", state.subtitleOffsetMs / 1000.0)
        val formattedOffset = if (state.subtitleOffsetMs > 0) "+$s" else s
        TextValue.StringResource(R.string.player_subtitle_offset_value, formattedOffset)
    } else {
        TextValue.StringResource(R.string.player_subtitle_timing_on_time)
    }

    return FilmanOverlayMenuItem.NestedMenu(
        id = "menu_subtitle_sync",
        label = TextValue.StringResource(R.string.player_subtitle_fix_sync),
        value = menuValue,
        items = items,
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
