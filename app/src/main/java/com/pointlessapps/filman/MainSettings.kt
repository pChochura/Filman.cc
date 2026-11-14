package com.pointlessapps.filman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

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
    onInitialAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onInitialAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onSecondaryAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryTimerAmountToggled: (Long) -> Unit,
    onInitialAppearancePercentageToggled: (Long) -> Unit,
    onSecondaryAppearancePercentageToggled: (Long) -> Unit,
    onMoveSourceUp: (Int) -> Unit,
    onMoveSourceDown: (Int) -> Unit,
    onToggleSource: (Int) -> Unit,
    onMoveExtractorUp: (Int, Int) -> Unit,
    onMoveExtractorDown: (Int, Int) -> Unit,
    onToggleExtractor: (Int, Int) -> Unit,
    onPreferredQualitySelected: (String) -> Unit,
    onAutoPlayNextEpisodeToggled: (Boolean) -> Unit,
) = buildList {
    add(
        FilmanOverlayMenuItem.Header(
            id = "playback_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_playback),
        ),
    )

    buildSourcesPrioritySettings(
        sourcesPriority,
        onMoveSourceUp,
        onMoveSourceDown,
        onToggleSource,
        onMoveExtractorUp,
        onMoveExtractorDown,
        onToggleExtractor,
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
}

@Composable
@ReadOnlyComposable
private fun MutableList<FilmanOverlayMenuItem>.buildSourcesPrioritySettings(
    sourcesPriority: List<SourcePriorityConfig>,
    onMoveSourceUp: (Int) -> Unit,
    onMoveSourceDown: (Int) -> Unit,
    onToggleSource: (Int) -> Unit,
    onMoveExtractorUp: (Int, Int) -> Unit,
    onMoveExtractorDown: (Int, Int) -> Unit,
    onToggleExtractor: (Int, Int) -> Unit,
) {
    if (sourcesPriority.isNotEmpty()) {
        val sourcesItems = listOf(
            FilmanOverlayMenuItem.Header(
                id = "sources_priority_header",
                label = TextValue.StringResource(R.string.overlay_menu_sources_priority_description),
            ),
        ) + sourcesPriority.mapIndexed { sourceIndex, sourceConfig ->
            FilmanOverlayMenuItem.ReorderableOption(
                id = sourceConfig.name,
                label = TextValue.DynamicString(sourceConfig.name.replaceFirstChar { it.uppercase() }),
                value = stringResource(
                    if (sourceConfig.isEnabled) {
                        R.string.common_enabled_true
                    } else {
                        R.string.common_enabled_false
                    },
                ),
                trailingButtons = listOf(
                    FilmanOverlayMenuItem.ReorderableOption.TrailingButton.NestedMenu(
                        icon = R.drawable.ic_more_vert,
                        items = listOf(
                            FilmanOverlayMenuItem.Header(
                                id = "extractors_priority_header_${sourceConfig.name}",
                                label = TextValue.StringResource(R.string.overlay_menu_extractors_priority_description),
                            ),
                            FilmanOverlayMenuItem.Button(
                                id = "toggle_${sourceConfig.name}",
                                label = TextValue.StringResource(
                                    if (sourceConfig.isEnabled) {
                                        R.string.overlay_menu_disable_source
                                    } else {
                                        R.string.overlay_menu_enable_source
                                    },
                                ),
                                value = stringResource(
                                    if (sourceConfig.isEnabled) {
                                        R.string.common_enabled_true
                                    } else {
                                        R.string.common_enabled_false
                                    },
                                ),
                                onClick = { onToggleSource(sourceIndex) },
                            ),
                        ) + sourceConfig.extractors.mapIndexed { extractorIndex, extractorConfig ->
                            FilmanOverlayMenuItem.ReorderableOption(
                                id = "${sourceConfig.name}_${extractorConfig.name}",
                                label = TextValue.DynamicString(extractorConfig.name),
                                trailingButtons = listOf(
                                    FilmanOverlayMenuItem.ReorderableOption.TrailingButton.Toggle(
                                        isEnabled = extractorConfig.isEnabled,
                                        onToggle = {
                                            onToggleExtractor(
                                                sourceIndex,
                                                extractorIndex,
                                            )
                                        },
                                        contentDescription = if (extractorConfig.isEnabled) {
                                            R.string.overlay_menu_disable_source
                                        } else {
                                            R.string.overlay_menu_enable_source
                                        },
                                    ),
                                ),
                                onMoveUp = if (extractorIndex > 0) {
                                    { onMoveExtractorUp(sourceIndex, extractorIndex) }
                                } else {
                                    null
                                },
                                onMoveDown = if (extractorIndex < sourceConfig.extractors.size - 1) {
                                    { onMoveExtractorDown(sourceIndex, extractorIndex) }
                                } else {
                                    null
                                },
                            )
                        },
                        contentDescription = R.string.common_more_options,
                    ),
                ),
                onMoveUp = if (sourceIndex > 0) {
                    { onMoveSourceUp(sourceIndex) }
                } else {
                    null
                },
                onMoveDown = if (sourceIndex < sourcesPriority.size - 1) {
                    { onMoveSourceDown(sourceIndex) }
                } else {
                    null
                },
            )
        }

        add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "sources_priority_settings",
                label = TextValue.StringResource(R.string.overlay_menu_sources_priority),
                value = null,
                items = sourcesItems,
            ),
        )
    }
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
