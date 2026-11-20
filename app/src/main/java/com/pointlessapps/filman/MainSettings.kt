package com.pointlessapps.filman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.model.AudioPreferenceMode
import com.pointlessapps.filman.data.model.ContentCategory
import com.pointlessapps.filman.data.model.ContentRule
import com.pointlessapps.filman.data.model.ContentRulesPreferences
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.player.buildLektorNestedMenu

@Composable
@ReadOnlyComposable
internal fun getPlaybackSettings(
    preferredQuality: String,
    autoPlayNextEpisode: Boolean,
    initialAppearanceType: NextEpisodeAppearance,
    initialAppearanceOffset: Long,
    secondaryAppearanceType: NextEpisodeAppearance,
    secondaryAppearanceOffset: Long,
    secondaryTimerAmount: Long,
    initialAppearancePercentage: Long,
    secondaryAppearancePercentage: Long,
    isLektorEnabled: Boolean,
    lektorHideSubtitles: Boolean,
    lektorVoiceId: String,
    lektorDuckingVolume: Float,
    lektorTimingOffsetMs: Int,
    lektorEdgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
    lektorEdgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
    lektorEdgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    lektorEdgeCalmMode: Boolean = LektorEdgeVoiceOptions.DEFAULT_CALM_MODE,
    downloadedLektorVoices: Set<String>,
    lektorModelDownloadProgress: Float?,
    isLektorModelExtracting: Boolean,
    systemVoices: SystemVoiceList,
    contentRules: ContentRulesPreferences = ContentRulesPreferences(),
    onContentRuleChanged: (ContentCategory, ContentRule) -> Unit = { _, _ -> },
    onInitialAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onInitialAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onSecondaryAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryTimerAmountToggled: (Long) -> Unit,
    onInitialAppearancePercentageToggled: (Long) -> Unit,
    onSecondaryAppearancePercentageToggled: (Long) -> Unit,
    onPreferredQualitySelected: (String) -> Unit,
    onAutoPlayNextEpisodeToggled: (Boolean) -> Unit,
    onToggleLektor: (Boolean) -> Unit,
    onSetLektorHideSubtitles: (Boolean) -> Unit,
    onSelectLektorVoice: (String) -> Unit,
    onChangeLektorDuckingVolume: (Float) -> Unit,
    onChangeLektorTimingOffset: (Int) -> Unit,
    onChangeLektorEdgeRate: (String) -> Unit = {},
    onChangeLektorEdgePitch: (String) -> Unit = {},
    onChangeLektorEdgeVolume: (String) -> Unit = {},
    onChangeLektorEdgeCalmMode: (Boolean) -> Unit = {},
    onDownloadLektorModel: (String) -> Unit,
    onDeleteLektorModel: (String) -> Unit,
    onPreviewSystemVoice: (String?) -> Unit,
    onRefreshSystemVoices: () -> Unit,
    onTestVoice: () -> Unit = {},
) = buildList {
    add(
        TiwiOverlayMenuItem.Header(
            id = "playback_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_playback),
        ),
    )

    buildPreferredQualitySettings(preferredQuality, onPreferredQualitySelected)
    buildContentRulesSettings(contentRules, onContentRuleChanged)

    buildAutoPlaySettings(
        autoPlayNextEpisode = autoPlayNextEpisode,
        initialAppearanceType = initialAppearanceType,
        initialAppearanceOffset = initialAppearanceOffset,
        secondaryAppearanceType = secondaryAppearanceType,
        secondaryAppearanceOffset = secondaryAppearanceOffset,
        secondaryTimerAmount = secondaryTimerAmount,
        initialAppearancePercentage = initialAppearancePercentage,
        secondaryAppearancePercentage = secondaryAppearancePercentage,
        onInitialAppearanceTypeToggled = onInitialAppearanceTypeToggled,
        onInitialAppearanceOffsetToggled = onInitialAppearanceOffsetToggled,
        onSecondaryAppearanceTypeToggled = onSecondaryAppearanceTypeToggled,
        onSecondaryAppearanceOffsetToggled = onSecondaryAppearanceOffsetToggled,
        onSecondaryTimerAmountToggled = onSecondaryTimerAmountToggled,
        onInitialAppearancePercentageToggled = onInitialAppearancePercentageToggled,
        onSecondaryAppearancePercentageToggled = onSecondaryAppearancePercentageToggled,
        onAutoPlayNextEpisodeToggled = onAutoPlayNextEpisodeToggled,
    )

    add(
        buildLektorNestedMenu(
            isLektorEnabled = isLektorEnabled,
            lektorVoiceId = lektorVoiceId,
            downloadedVoices = downloadedLektorVoices,
            downloadProgress = lektorModelDownloadProgress,
            isExtracting = isLektorModelExtracting,
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
}

@Composable
@ReadOnlyComposable
private fun MutableList<TiwiOverlayMenuItem>.buildPreferredQualitySettings(
    preferredQuality: String,
    onPreferredQualitySelected: (String) -> Unit,
) {
    val qualityItems = SettingsConstants.Quality.ALL.map { quality ->
        TiwiOverlayMenuItem.Option(
            id = "quality_$quality",
            label = if (quality == SettingsConstants.Quality.AUTO) {
                TextValue.StringResource(R.string.overlay_menu_quality_auto)
            } else {
                TextValue.DynamicString(quality)
            },
            isSelected = quality == preferredQuality,
            onClick = { onPreferredQualitySelected(quality) },
        )
    }

    add(
        TiwiOverlayMenuItem.NestedMenu(
            id = "preferred_quality",
            label = TextValue.StringResource(R.string.overlay_menu_preferred_quality),
            value = if (preferredQuality == SettingsConstants.Quality.AUTO) {
                stringResource(R.string.overlay_menu_quality_auto)
            } else {
                preferredQuality
            },
            items = qualityItems,
        ),
    )
}

@Composable
@ReadOnlyComposable
private fun MutableList<TiwiOverlayMenuItem>.buildContentRulesSettings(
    contentRules: ContentRulesPreferences,
    onContentRuleChanged: (ContentCategory, ContentRule) -> Unit,
) {
    val items = ContentCategory.entries.map { category ->
        val rule = contentRules.getRule(category)
        val categoryLabelRes = when (category) {
            ContentCategory.KIDS_FAMILY -> R.string.content_category_kids
            ContentCategory.ANIME -> R.string.content_category_anime
            ContentCategory.NATIVE_DOMESTIC -> R.string.content_category_native_pl
            ContentCategory.FOREIGN_MAINSTREAM -> R.string.content_category_foreign_en
            ContentCategory.FOREIGN_WORLD -> R.string.content_category_foreign_world
            ContentCategory.DOCUMENTARY -> R.string.content_category_documentary
        }
        val currentModeRes = when (rule.resolvedAudioMode) {
            AudioPreferenceMode.DUBBING_PREFERRED -> R.string.audio_mode_dubbing
            AudioPreferenceMode.LEKTOR_PREFERRED -> R.string.audio_mode_lektor
            AudioPreferenceMode.ORIGINAL_SUBTITLES -> R.string.audio_mode_original_subtitles
            AudioPreferenceMode.ORIGINAL_ONLY -> R.string.audio_mode_original_only
        }
        val modeOptions = AudioPreferenceMode.entries.map { mode ->
            val modeLabelRes = when (mode) {
                AudioPreferenceMode.DUBBING_PREFERRED -> R.string.audio_mode_dubbing
                AudioPreferenceMode.LEKTOR_PREFERRED -> R.string.audio_mode_lektor
                AudioPreferenceMode.ORIGINAL_SUBTITLES -> R.string.audio_mode_original_subtitles
                AudioPreferenceMode.ORIGINAL_ONLY -> R.string.audio_mode_original_only
            }
            TiwiOverlayMenuItem.Option(
                id = "mode_${category.key}_${mode.value}",
                label = TextValue.StringResource(modeLabelRes),
                isSelected = rule.resolvedAudioMode == mode,
                onClick = {
                    onContentRuleChanged(category, rule.copy(audioMode = mode.value))
                },
            )
        }
        TiwiOverlayMenuItem.NestedMenu(
            id = "rule_${category.key}",
            label = TextValue.StringResource(categoryLabelRes),
            value = stringResource(currentModeRes),
            items = modeOptions,
        )
    }

    add(
        TiwiOverlayMenuItem.NestedMenu(
            id = "content_rules_menu",
            label = TextValue.StringResource(R.string.overlay_menu_content_rules),
            value = null,
            items = items,
        ),
    )
}
