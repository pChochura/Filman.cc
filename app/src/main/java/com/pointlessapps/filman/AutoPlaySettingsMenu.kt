package com.pointlessapps.filman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

@Composable
@ReadOnlyComposable
internal fun MutableList<TiwiOverlayMenuItem>.buildAutoPlaySettings(
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
        TiwiOverlayMenuItem.Option(
            id = "initial_type_$type",
            label = TextValue.StringResource(initialAppearanceLabelRes(type)),
            isSelected = initialAppearanceType == type,
            onClick = { onInitialAppearanceTypeToggled(type) },
        )
    }

    val offsetOptions = listOf(30L, 45L, 60L, 75L, 90L, 105L, 120L)
    val initialOffsetItems = offsetOptions.map { offset ->
        TiwiOverlayMenuItem.Option(
            id = "initial_offset_$offset",
            label = TextValue.StringResource(R.string.common_next_episode_seconds_format, offset),
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
        TiwiOverlayMenuItem.Option(
            id = "secondary_type_$type",
            label = TextValue.StringResource(secondaryAppearanceLabelRes(type)),
            isSelected = secondaryAppearanceType == type,
            onClick = { onSecondaryAppearanceTypeToggled(type) },
        )
    }

    val secondaryOffsetItems = offsetOptions.filter { it < initialAppearanceOffset }.map { offset ->
        TiwiOverlayMenuItem.Option(
            id = "secondary_offset_$offset",
            label = TextValue.StringResource(R.string.common_next_episode_seconds_format, offset),
            isSelected = secondaryAppearanceOffset == offset,
            onClick = { onSecondaryAppearanceOffsetToggled(offset) },
        )
    }

    val secondaryTimerAmountItems = listOf(5L, 10L, 15L, 20L).map { amount ->
        TiwiOverlayMenuItem.Option(
            id = "secondary_timer_$amount",
            label = TextValue.StringResource(R.string.common_next_episode_seconds_format, amount),
            isSelected = secondaryTimerAmount == amount,
            onClick = { onSecondaryTimerAmountToggled(amount) },
        )
    }

    val percentageOptions = listOf(1L, 2L, 3L, 4L, 5L)
    val initialPercentageItems = percentageOptions.map { percentage ->
        TiwiOverlayMenuItem.Option(
            id = "initial_percentage_$percentage",
            label = TextValue.StringResource(R.string.common_next_episode_percentage_format, percentage),
            isSelected = initialAppearancePercentage == percentage,
            onClick = { onInitialAppearancePercentageToggled(percentage) },
        )
    }

    val secondaryPercentageItems = percentageOptions.filter { it < initialAppearancePercentage }.map { percentage ->
        TiwiOverlayMenuItem.Option(
            id = "secondary_percentage_$percentage",
            label = TextValue.StringResource(R.string.common_next_episode_percentage_format, percentage),
            isSelected = secondaryAppearancePercentage == percentage,
            onClick = { onSecondaryAppearancePercentageToggled(percentage) },
        )
    }

    val initialPhaseNestedItems = buildList {
        add(
            TiwiOverlayMenuItem.Header(
                id = "initial_phase_desc",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_phase_desc),
            ),
        )
        add(
            TiwiOverlayMenuItem.NestedMenu(
                id = "initial_appearance_type",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_type),
                value = stringResource(initialAppearanceLabelRes(initialAppearanceType)),
                items = initialTypeItems,
            ),
        )
        if (initialAppearanceType != NextEpisodeAppearance.HIDE) {
            add(
                TiwiOverlayMenuItem.NestedMenu(
                    id = "initial_percentage",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_percentage),
                    value = stringResource(R.string.common_next_episode_percentage_format, initialAppearancePercentage),
                    items = initialPercentageItems,
                ),
            )
            add(
                TiwiOverlayMenuItem.NestedMenu(
                    id = "initial_appearance_offset",
                    label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_offset),
                    value = stringResource(R.string.common_next_episode_seconds_format, initialAppearanceOffset),
                    items = initialOffsetItems,
                ),
            )
        }
    }

    val secondaryPhaseNestedItems = buildList {
        add(
            TiwiOverlayMenuItem.Header(
                id = "main_phase_desc",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_main_phase_desc),
            ),
        )
        add(
            TiwiOverlayMenuItem.NestedMenu(
                id = "secondary_appearance_type",
                label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_type),
                value = stringResource(secondaryAppearanceLabelRes(secondaryAppearanceType)),
                items = secondaryTypeItems,
            ),
        )
        if (secondaryAppearanceType != NextEpisodeAppearance.HIDE) {
            if (secondaryPercentageItems.isNotEmpty()) {
                add(
                    TiwiOverlayMenuItem.NestedMenu(
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
                add(
                    TiwiOverlayMenuItem.NestedMenu(
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
                add(
                    TiwiOverlayMenuItem.NestedMenu(
                        id = "secondary_timer_amount",
                        label = TextValue.StringResource(R.string.overlay_menu_next_episode_secondary_timer),
                        value = stringResource(R.string.common_next_episode_seconds_format, secondaryTimerAmount),
                        items = secondaryTimerAmountItems,
                    ),
                )
            }
        }
    }

    val nextEpisodeButtonItems = listOf(
        TiwiOverlayMenuItem.NestedMenu(
            id = "initial_phase",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_initial_phase),
            value = stringResource(initialAppearanceLabelRes(initialAppearanceType)),
            items = initialPhaseNestedItems,
        ),
        TiwiOverlayMenuItem.NestedMenu(
            id = "main_phase",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_main_phase),
            value = stringResource(secondaryAppearanceLabelRes(secondaryAppearanceType)),
            items = secondaryPhaseNestedItems,
        ),
    )

    val nextEpisodeNestedItems = listOf(
        TiwiOverlayMenuItem.NestedMenu(
            id = "autoplay_next_episode",
            label = TextValue.StringResource(R.string.overlay_menu_autoplay_next),
            value = stringResource(
                if (autoPlayNextEpisode) R.string.overlay_menu_autoplay_enabled else R.string.overlay_menu_autoplay_disabled,
            ),
            items = listOf(
                TiwiOverlayMenuItem.Option(
                    id = "autoplay_true",
                    label = TextValue.StringResource(R.string.overlay_menu_autoplay_enabled),
                    isSelected = autoPlayNextEpisode,
                    onClick = { onAutoPlayNextEpisodeToggled(true) },
                ),
                TiwiOverlayMenuItem.Option(
                    id = "autoplay_false",
                    label = TextValue.StringResource(R.string.overlay_menu_autoplay_disabled),
                    isSelected = !autoPlayNextEpisode,
                    onClick = { onAutoPlayNextEpisodeToggled(false) },
                ),
            ),
        ),
        TiwiOverlayMenuItem.NestedMenu(
            id = "next_episode_button",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_button),
            value = null,
            items = nextEpisodeButtonItems,
        ),
    )

    add(
        TiwiOverlayMenuItem.NestedMenu(
            id = "next_episode_settings",
            label = TextValue.StringResource(R.string.overlay_menu_next_episode_settings),
            value = null,
            items = nextEpisodeNestedItems,
        ),
    )
}

private fun initialAppearanceLabelRes(type: NextEpisodeAppearance): Int = when (type) {
    NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_show
    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
    NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
    else -> R.string.common_next_episode_appearance_show
}

private fun secondaryAppearanceLabelRes(type: NextEpisodeAppearance): Int = when (type) {
    NextEpisodeAppearance.SHOW_WITH_TIMER -> R.string.common_next_episode_appearance_show_with_timer
    NextEpisodeAppearance.SHOW -> R.string.common_next_episode_appearance_just_show
    NextEpisodeAppearance.SHOW_IN_OVERLAY -> R.string.common_next_episode_appearance_show_in_overlay
    NextEpisodeAppearance.HIDE -> R.string.common_next_episode_appearance_dont_show
}
