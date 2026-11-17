package com.pointlessapps.filman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.player.buildLektorNestedMenu

@Composable
@ReadOnlyComposable
internal fun getPlaybackSettings(
    sourcesPriority: List<SourcePriorityConfig>,
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
    downloadedLektorVoices: Set<String>,
    lektorModelDownloadProgress: Float?,
    isLektorModelExtracting: Boolean,
    systemVoices: SystemVoiceList,
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
    onDownloadLektorModel: (String) -> Unit,
    onDeleteLektorModel: (String) -> Unit,
    onPreviewSystemVoice: (String?) -> Unit,
    onRefreshSystemVoices: () -> Unit,
    onTestVoice: () -> Unit = {},
) = buildList {
    add(
        FilmanOverlayMenuItem.Header(
            id = "playback_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_playback),
        ),
    )

    buildPrefferedQualitySettings(
        preferredQuality,
        onPreferredQualitySelected,
    )

    buildAutoPlaySettings(
        autoPlayNextEpisode,
        initialAppearanceType,
        initialAppearanceOffset,
        secondaryAppearanceType,
        secondaryAppearanceOffset,
        secondaryTimerAmount,
        initialAppearancePercentage,
        secondaryAppearancePercentage,
        onInitialAppearanceTypeToggled,
        onInitialAppearanceOffsetToggled,
        onSecondaryAppearanceTypeToggled,
        onSecondaryAppearanceOffsetToggled,
        onSecondaryTimerAmountToggled,
        onInitialAppearancePercentageToggled,
        onSecondaryAppearancePercentageToggled,
        onAutoPlayNextEpisodeToggled,
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
            onTestVoice = onTestVoice,
        ),
    )
}

@Composable
@ReadOnlyComposable
private fun MutableList<FilmanOverlayMenuItem>.buildPrefferedQualitySettings(
    preferredQuality: String,
    onPreferredQualitySelected: (String) -> Unit,
) {
    val qualityOptions = SettingsConstants.Quality.ALL
    val qualityItems =
        qualityOptions.map { quality ->
            FilmanOverlayMenuItem.Option(
                id = "quality_$quality",
                label =
                    if (quality == SettingsConstants.Quality.AUTO) {
                        TextValue.StringResource(R.string.overlay_menu_quality_auto)
                    } else {
                        TextValue.DynamicString(quality)
                    },
                isSelected = quality == preferredQuality,
                onClick = { onPreferredQualitySelected(quality) },
            )
        }

    add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "preferred_quality",
            label = TextValue.StringResource(R.string.overlay_menu_preferred_quality),
            value =
                if (preferredQuality == SettingsConstants.Quality.AUTO) {
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
private fun MutableList<FilmanOverlayMenuItem>.buildAutoPlaySettings(
    autoPlayNextEpisode: Boolean,
    initialAppearanceType: NextEpisodeAppearance,
    initialAppearanceOffset: Long,
    secondaryAppearanceType: NextEpisodeAppearance,
    secondaryAppearanceOffset: Long,
    secondaryTimerAmount: Long,
    initialAppearancePercentage: Long,
    secondaryAppearancePercentage: Long,
    onInitialAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onInitialAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onSecondaryAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryTimerAmountToggled: (Long) -> Unit,
    onInitialAppearancePercentageToggled: (Long) -> Unit,
    onSecondaryAppearancePercentageToggled: (Long) -> Unit,
    onAutoPlayNextEpisodeToggled: (Boolean) -> Unit,
) {
    val initialTypeItems =
        listOf(
            NextEpisodeAppearance.SHOW,
            NextEpisodeAppearance.SHOW_IN_OVERLAY,
            NextEpisodeAppearance.HIDE,
        ).map { type ->
            FilmanOverlayMenuItem.Option(
                id = "initial_type_$type",
                label =
                    TextValue.StringResource(
                        when (type) {
                            NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_show
                            NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
                            NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
                            else -> R.string.common_next_episode_appearance_show
                        },
                    ),
                isSelected = initialAppearanceType == type,
                onClick = { onInitialAppearanceTypeToggled(type) },
            )
        }

    val offsetOptions = listOf(30L, 45L, 60L, 75L, 90L, 105L, 120L)
    val initialOffsetItems =
        offsetOptions.map { offset ->
            FilmanOverlayMenuItem.Option(
                id = "initial_offset_$offset",
                label = TextValue.StringResource(R.string.common_next_episode_seconds_format, offset),
                isSelected = initialAppearanceOffset == offset,
                onClick = { onInitialAppearanceOffsetToggled(offset) },
            )
        }

    val secondaryTypeItems =
        listOf(
            NextEpisodeAppearance.SHOW_WITH_TIMER,
            NextEpisodeAppearance.SHOW,
            NextEpisodeAppearance.SHOW_IN_OVERLAY,
            NextEpisodeAppearance.HIDE,
        ).map { type ->
            FilmanOverlayMenuItem.Option(
                id = "secondary_type_$type",
                label =
                    TextValue.StringResource(
                        when (type) {
                            NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.common_next_episode_appearance_show_with_timer
                            NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_just_show
                            NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
                            NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
                        },
                    ),
                isSelected = secondaryAppearanceType == type,
                onClick = { onSecondaryAppearanceTypeToggled(type) },
            )
        }

    val secondaryOffsetItems =
        offsetOptions.filter { it < initialAppearanceOffset }.map { offset ->
            FilmanOverlayMenuItem.Option(
                id = "secondary_offset_$offset",
                label = TextValue.StringResource(R.string.common_next_episode_seconds_format, offset),
                isSelected = secondaryAppearanceOffset == offset,
                onClick = { onSecondaryAppearanceOffsetToggled(offset) },
            )
        }

    val timerAmountOptions = listOf(5L, 10L, 15L, 20L)
    val secondaryTimerAmountItems =
        timerAmountOptions.map { amount ->
            FilmanOverlayMenuItem.Option(
                id = "secondary_timer_$amount",
                label = TextValue.StringResource(R.string.common_next_episode_seconds_format, amount),
                isSelected = secondaryTimerAmount == amount,
                onClick = { onSecondaryTimerAmountToggled(amount) },
            )
        }

    val percentageOptions = listOf(1L, 2L, 3L, 4L, 5L)
    val initialPercentageItems =
        percentageOptions.map { percentage ->
            FilmanOverlayMenuItem.Option(
                id = "initial_percentage_$percentage",
                label = TextValue.StringResource(
                    R.string.common_next_episode_percentage_format,
                    percentage,
                ),
                isSelected = initialAppearancePercentage == percentage,
                onClick = { onInitialAppearancePercentageToggled(percentage) },
            )
        }

    val secondaryPercentageItems =
        percentageOptions.filter { it < initialAppearancePercentage }.map { percentage ->
            FilmanOverlayMenuItem.Option(
                id = "secondary_percentage_$percentage",
                label = TextValue.StringResource(
                    R.string.common_next_episode_percentage_format,
                    percentage,
                ),
                isSelected = secondaryAppearancePercentage == percentage,
                onClick = { onSecondaryAppearancePercentageToggled(percentage) },
            )
        }

    val initialPhaseNestedItems = mutableListOf<FilmanOverlayMenuItem>()
    initialPhaseNestedItems.add(
        FilmanOverlayMenuItem.Header(
            id = "initial_phase_desc",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_phase_desc),
        ),
    )
    initialPhaseNestedItems.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "initial_appearance_type",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_type),
            value =
                stringResource(
                    when (initialAppearanceType) {
                        NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_show
                        NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
                        NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
                        else -> R.string.common_next_episode_appearance_show
                    },
                ),
            items = initialTypeItems,
        ),
    )
    if (initialAppearanceType != NextEpisodeAppearance.HIDE) {
        initialPhaseNestedItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "initial_percentage",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_percentage),
                value = stringResource(
                    R.string.common_next_episode_percentage_format,
                    initialAppearancePercentage,
                ),
                items = initialPercentageItems,
            ),
        )
        initialPhaseNestedItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "initial_appearance_offset",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_offset),
                value = stringResource(
                    R.string.common_next_episode_seconds_format,
                    initialAppearanceOffset,
                ),
                items = initialOffsetItems,
            ),
        )
    }

    val secondaryPhaseNestedItems = mutableListOf<FilmanOverlayMenuItem>()
    secondaryPhaseNestedItems.add(
        FilmanOverlayMenuItem.Header(
            id = "main_phase_desc",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_main_phase_desc),
        ),
    )
    secondaryPhaseNestedItems.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "secondary_appearance_type",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_type),
            value =
                stringResource(
                    when (secondaryAppearanceType) {
                        NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.common_next_episode_appearance_show_with_timer
                        NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_just_show
                        NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
                        NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
                    },
                ),
            items = secondaryTypeItems,
        ),
    )
    if (secondaryAppearanceType != NextEpisodeAppearance.HIDE) {
        if (secondaryPercentageItems.isNotEmpty()) {
            secondaryPhaseNestedItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    id = "secondary_percentage",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_percentage),
                    value = stringResource(
                        R.string.common_next_episode_percentage_format,
                        secondaryAppearancePercentage,
                    ),
                    items = secondaryPercentageItems,
                ),
            )
        }
        if (secondaryOffsetItems.isNotEmpty()) {
            secondaryPhaseNestedItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    id = "secondary_appearance_offset",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_offset),
                    value = stringResource(
                        R.string.common_next_episode_seconds_format,
                        secondaryAppearanceOffset,
                    ),
                    items = secondaryOffsetItems,
                ),
            )
        }

        if (secondaryAppearanceType == NextEpisodeAppearance.SHOW_WITH_TIMER) {
            secondaryPhaseNestedItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    id = "secondary_timer_amount",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_timer),
                    value = stringResource(
                        R.string.common_next_episode_seconds_format,
                        secondaryTimerAmount,
                    ),
                    items = secondaryTimerAmountItems,
                ),
            )
        }
    }

    val nextEpisodeButtonItems =
        listOf(
            FilmanOverlayMenuItem.NestedMenu(
                id = "initial_phase",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_phase),
                value =
                    stringResource(
                        when (initialAppearanceType) {
                            NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_show
                            NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
                            NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
                            else -> R.string.common_next_episode_appearance_show
                        },
                    ),
                items = initialPhaseNestedItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                id = "main_phase",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_main_phase),
                value =
                    stringResource(
                        when (secondaryAppearanceType) {
                            NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.common_next_episode_appearance_show_with_timer
                            NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_just_show
                            NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
                            NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
                        },
                    ),
                items = secondaryPhaseNestedItems,
            ),
        )

    val nextEpisodeNestedItems =
        listOf(
            FilmanOverlayMenuItem.NestedMenu(
                id = "autoplay_next_episode",
                label = TextValue.StringResource(R.string.overlay_menu_autoplay_next),
                value =
                    stringResource(
                        if (autoPlayNextEpisode) {
                            R.string.overlay_menu_autoplay_enabled
                        } else {
                            R.string.overlay_menu_autoplay_disabled
                        },
                    ),
                items =
                    listOf(
                        FilmanOverlayMenuItem.Option(
                            id = "autoplay_true",
                            label = TextValue.StringResource(R.string.overlay_menu_autoplay_enabled),
                            isSelected = autoPlayNextEpisode,
                            onClick = { onAutoPlayNextEpisodeToggled(true) },
                        ),
                        FilmanOverlayMenuItem.Option(
                            id = "autoplay_false",
                            label = TextValue.StringResource(R.string.overlay_menu_autoplay_disabled),
                            isSelected = !autoPlayNextEpisode,
                            onClick = { onAutoPlayNextEpisodeToggled(false) },
                        ),
                    ),
            ),
            FilmanOverlayMenuItem.NestedMenu(
                id = "next_episode_button",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_button),
                value = null,
                items = nextEpisodeButtonItems,
            ),
        )

    add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "next_episode_settings",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_settings),
            value = null,
            items = nextEpisodeNestedItems,
        ),
    )
}

@Composable
@ReadOnlyComposable
internal fun MutableList<FilmanOverlayMenuItem>.buildScreensaverSettings(
    isScreensaverEnabled: Boolean,
    screensaverInactivityTime: Long,
    screensaverSlideDuration: Long,
    onScreensaverEnabledToggled: (Boolean) -> Unit,
    onScreensaverInactivityTimeChanged: (Long) -> Unit,
    onScreensaverSlideDurationChanged: (Long) -> Unit,
) {
    val enabledItems = listOf(
        FilmanOverlayMenuItem.Option(
            id = "screensaver_true",
            label = TextValue.StringResource(R.string.common_enabled_true),
            isSelected = isScreensaverEnabled,
            onClick = { onScreensaverEnabledToggled(true) },
        ),
        FilmanOverlayMenuItem.Option(
            id = "screensaver_false",
            label = TextValue.StringResource(R.string.common_enabled_false),
            isSelected = !isScreensaverEnabled,
            onClick = { onScreensaverEnabledToggled(false) },
        ),
    )

    val inactivityTimes = listOf(60_000L, 120_000L, 180_000L, 240_000L, 300_000L)
    val inactivityTimeItems = inactivityTimes.map { timeMs ->
        val minutes = timeMs / 60_000L
        FilmanOverlayMenuItem.Option(
            id = "inactivity_$timeMs",
            label = TextValue.PluralResource(
                R.plurals.common_time_format_minutes,
                minutes.toInt(),
                listOf(minutes),
            ),
            isSelected = screensaverInactivityTime == timeMs,
            onClick = { onScreensaverInactivityTimeChanged(timeMs) },
        )
    }

    val slideDurations = listOf(5_000L, 10_000L, 15_000L, 20_000L)
    val slideDurationItems = slideDurations.map { timeMs ->
        val seconds = timeMs / 1000L
        FilmanOverlayMenuItem.Option(
            id = "slide_$timeMs",
            label = TextValue.PluralResource(
                R.plurals.common_time_format_seconds,
                seconds.toInt(),
                listOf(seconds),
            ),
            isSelected = screensaverSlideDuration == timeMs,
            onClick = { onScreensaverSlideDurationChanged(timeMs) },
        )
    }

    val nestedItems = mutableListOf<FilmanOverlayMenuItem>()
    nestedItems.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "screensaver_enabled",
            label = TextValue.StringResource(R.string.common_enabled),
            value = stringResource(
                if (isScreensaverEnabled) {
                    R.string.common_enabled_true
                } else {
                    R.string.common_enabled_false
                },
            ),
            items = enabledItems,
        ),
    )

    if (isScreensaverEnabled) {
        nestedItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "screensaver_inactivity",
                label = TextValue.StringResource(R.string.common_inactivity_time),
                value = pluralStringResource(
                    R.plurals.common_time_format_minutes,
                    (screensaverInactivityTime / 60_000L).toInt(),
                    screensaverInactivityTime / 60_000L,
                ),
                items = inactivityTimeItems,
            ),
        )
        nestedItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "screensaver_slide",
                label = TextValue.StringResource(R.string.common_slide_duration),
                value = pluralStringResource(
                    R.plurals.common_time_format_seconds,
                    (screensaverSlideDuration / 1000L).toInt(),
                    screensaverSlideDuration / 1000L,
                ),
                items = slideDurationItems,
            ),
        )
    }

    add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "screensaver_settings",
            label = TextValue.StringResource(R.string.common_screensaver_settings),
            value = null,
            items = nestedItems,
        ),
    )
}
