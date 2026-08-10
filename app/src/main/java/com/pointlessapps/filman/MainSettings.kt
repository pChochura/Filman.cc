package com.pointlessapps.filman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

@Composable
@ReadOnlyComposable
internal fun getPlaybackSettings(
    extractorsPriority: List<String>,
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
    onMoveExtractorUp: (Int) -> Unit,
    onMoveExtractorDown: (Int) -> Unit,
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
        extractorsPriority,
        onMoveExtractorUp,
        onMoveExtractorDown,
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
    extractorsPriority: List<String>,
    onMoveExtractorUp: (Int) -> Unit,
    onMoveExtractorDown: (Int) -> Unit,
) {
    if (extractorsPriority.isNotEmpty()) {
        val extractorsItems = extractorsPriority.mapIndexed { index, extractor ->
            FilmanOverlayMenuItem.ReorderableOption(
                id = extractor,
                label = TextValue.DynamicString(extractor),
                onMoveUp = if (index > 0) {
                    { onMoveExtractorUp(index) }
                } else {
                    null
                },
                onMoveDown = if (index < extractorsPriority.size - 1) {
                    { onMoveExtractorDown(index) }
                } else {
                    null
                },
            )
        }
        add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "extractors_priority",
                label = TextValue.StringResource(R.string.overlay_menu_sources_priority),
                value = null,
                items = extractorsItems,
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
    val qualityItems = qualityOptions.map { quality ->
        FilmanOverlayMenuItem.Option(
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
        FilmanOverlayMenuItem.NestedMenu(
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
    val initialTypeItems = listOf(
        NextEpisodeAppearance.SHOW,
        NextEpisodeAppearance.SHOW_IN_OVERLAY,
        NextEpisodeAppearance.HIDE,
    ).map { type ->
        FilmanOverlayMenuItem.Option(
            id = "initial_type_$type",
            label = TextValue.StringResource(
                when (type) {
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                    else -> R.string.next_episode_appearance_show
                },
            ),
            isSelected = initialAppearanceType == type,
            onClick = { onInitialAppearanceTypeToggled(type) },
        )
    }

    val offsetOptions = listOf(30L, 45L, 60L, 75L, 90L, 105L, 120L)
    val initialOffsetItems = offsetOptions.map { offset ->
        FilmanOverlayMenuItem.Option(
            id = "initial_offset_$offset",
            label = TextValue.StringResource(R.string.next_episode_seconds_format, offset),
            isSelected = initialAppearanceOffset == offset,
            onClick = { onInitialAppearanceOffsetToggled(offset) },
        )
    }

    val secondaryTypeItems = listOf(
        NextEpisodeAppearance.SHOW_WITH_TIMER,
        NextEpisodeAppearance.SHOW,
        NextEpisodeAppearance.SHOW_IN_OVERLAY,
        NextEpisodeAppearance.HIDE,
    ).map { type ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_type_$type",
            label = TextValue.StringResource(
                when (type) {
                    NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.next_episode_appearance_show_with_timer
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_just_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                },
            ),
            isSelected = secondaryAppearanceType == type,
            onClick = { onSecondaryAppearanceTypeToggled(type) },
        )
    }

    val secondaryOffsetItems = offsetOptions.filter { it < initialAppearanceOffset }.map { offset ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_offset_$offset",
            label = TextValue.StringResource(R.string.next_episode_seconds_format, offset),
            isSelected = secondaryAppearanceOffset == offset,
            onClick = { onSecondaryAppearanceOffsetToggled(offset) },
        )
    }

    val timerAmountOptions = listOf(5L, 10L, 15L, 20L)
    val secondaryTimerAmountItems = timerAmountOptions.map { amount ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_timer_$amount",
            label = TextValue.StringResource(R.string.next_episode_seconds_format, amount),
            isSelected = secondaryTimerAmount == amount,
            onClick = { onSecondaryTimerAmountToggled(amount) },
        )
    }

    val percentageOptions = listOf(1L, 2L, 3L, 4L, 5L)
    val initialPercentageItems = percentageOptions.map { percentage ->
        FilmanOverlayMenuItem.Option(
            id = "initial_percentage_$percentage",
            label = TextValue.StringResource(R.string.next_episode_percentage_format, percentage),
            isSelected = initialAppearancePercentage == percentage,
            onClick = { onInitialAppearancePercentageToggled(percentage) },
        )
    }
    
    val secondaryPercentageItems = percentageOptions.filter { it < initialAppearancePercentage }.map { percentage ->
        FilmanOverlayMenuItem.Option(
            id = "secondary_percentage_$percentage",
            label = TextValue.StringResource(R.string.next_episode_percentage_format, percentage),
            isSelected = secondaryAppearancePercentage == percentage,
            onClick = { onSecondaryAppearancePercentageToggled(percentage) },
        )
    }

    val initialPhaseNestedItems = mutableListOf<FilmanOverlayMenuItem>()
    initialPhaseNestedItems.add(
        FilmanOverlayMenuItem.Header(
            id = "initial_phase_desc",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_phase_desc),
        )
    )
    initialPhaseNestedItems.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "initial_appearance_type",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_type),
            value = stringResource(
                when (initialAppearanceType) {
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                    else -> R.string.next_episode_appearance_show
                },
            ),
            items = initialTypeItems,
        )
    )
    if (initialAppearanceType != NextEpisodeAppearance.HIDE) {
        initialPhaseNestedItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "initial_percentage",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_percentage),
                value = stringResource(R.string.next_episode_percentage_format, initialAppearancePercentage),
                items = initialPercentageItems,
            )
        )
        initialPhaseNestedItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                id = "initial_appearance_offset",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_offset),
                value = stringResource(R.string.next_episode_seconds_format, initialAppearanceOffset),
                items = initialOffsetItems,
            )
        )
    }

    val secondaryPhaseNestedItems = mutableListOf<FilmanOverlayMenuItem>()
    secondaryPhaseNestedItems.add(
        FilmanOverlayMenuItem.Header(
            id = "main_phase_desc",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_main_phase_desc),
        )
    )
    secondaryPhaseNestedItems.add(
        FilmanOverlayMenuItem.NestedMenu(
            id = "secondary_appearance_type",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_type),
            value = stringResource(
                when (secondaryAppearanceType) {
                    NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.next_episode_appearance_show_with_timer
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_just_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                },
            ),
            items = secondaryTypeItems,
        )
    )
    if (secondaryAppearanceType != NextEpisodeAppearance.HIDE) {
        if (secondaryPercentageItems.isNotEmpty()) {
            secondaryPhaseNestedItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    id = "secondary_percentage",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_percentage),
                    value = stringResource(R.string.next_episode_percentage_format, secondaryAppearancePercentage),
                    items = secondaryPercentageItems,
                )
            )
        }
        if (secondaryOffsetItems.isNotEmpty()) {
            secondaryPhaseNestedItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    id = "secondary_appearance_offset",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_offset),
                    value = stringResource(R.string.next_episode_seconds_format, secondaryAppearanceOffset),
                    items = secondaryOffsetItems,
                )
            )
        }
        
        if (secondaryAppearanceType == NextEpisodeAppearance.SHOW_WITH_TIMER) {
            secondaryPhaseNestedItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    id = "secondary_timer_amount",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_timer),
                    value = stringResource(R.string.next_episode_seconds_format, secondaryTimerAmount),
                    items = secondaryTimerAmountItems,
                )
            )
        }
    }

    val nextEpisodeButtonItems = listOf(
        FilmanOverlayMenuItem.NestedMenu(
            id = "initial_phase",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_phase),
            value = stringResource(
                when (initialAppearanceType) {
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                    else -> R.string.next_episode_appearance_show
                },
            ),
            items = initialPhaseNestedItems,
        ),
        FilmanOverlayMenuItem.NestedMenu(
            id = "main_phase",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_main_phase),
            value = stringResource(
                when (secondaryAppearanceType) {
                    NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.next_episode_appearance_show_with_timer
                    NextEpisodeAppearance.SHOW -> R.string.next_episode_appearance_just_show
                    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.next_episode_appearance_show_in_overlay
                    NextEpisodeAppearance.HIDE -> R.string.next_episode_appearance_dont_show
                },
            ),
            items = secondaryPhaseNestedItems,
        ),
    )

    val nextEpisodeNestedItems = listOf(
        FilmanOverlayMenuItem.NestedMenu(
            id = "autoplay_next_episode",
            label = TextValue.StringResource(R.string.overlay_menu_autoplay_next),
            value = stringResource(
                if (autoPlayNextEpisode) {
                    R.string.overlay_menu_autoplay_enabled
                } else {
                    R.string.overlay_menu_autoplay_disabled
                },
            ),
            items = listOf(
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
        )
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
