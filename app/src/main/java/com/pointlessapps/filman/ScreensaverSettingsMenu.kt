package com.pointlessapps.filman

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

@Composable
@ReadOnlyComposable
internal fun MutableList<TiwiOverlayMenuItem>.buildScreensaverSettings(
    isScreensaverEnabled: Boolean,
    screensaverInactivityTime: Long,
    screensaverSlideDuration: Long,
    onScreensaverEnabledToggled: (Boolean) -> Unit,
    onScreensaverInactivityTimeChanged: (Long) -> Unit,
    onScreensaverSlideDurationChanged: (Long) -> Unit,
) {
    val enabledItems = listOf(
        TiwiOverlayMenuItem.Option(
            id = "screensaver_true",
            label = TextValue.StringResource(R.string.common_enabled_true),
            isSelected = isScreensaverEnabled,
            onClick = { onScreensaverEnabledToggled(true) },
        ),
        TiwiOverlayMenuItem.Option(
            id = "screensaver_false",
            label = TextValue.StringResource(R.string.common_enabled_false),
            isSelected = !isScreensaverEnabled,
            onClick = { onScreensaverEnabledToggled(false) },
        ),
    )

    val inactivityTimeItems = listOf(60_000L, 120_000L, 180_000L, 240_000L, 300_000L).map { timeMs ->
        val minutes = timeMs / 60_000L
        TiwiOverlayMenuItem.Option(
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

    val slideDurationItems = listOf(5_000L, 10_000L, 15_000L, 20_000L).map { timeMs ->
        val seconds = timeMs / 1000L
        TiwiOverlayMenuItem.Option(
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

    val nestedItems = buildList {
        add(
            TiwiOverlayMenuItem.NestedMenu(
                id = "screensaver_enabled",
                label = TextValue.StringResource(R.string.common_enabled),
                value = stringResource(
                    if (isScreensaverEnabled) R.string.common_enabled_true else R.string.common_enabled_false,
                ),
                items = enabledItems,
            ),
        )

        if (isScreensaverEnabled) {
            add(
                TiwiOverlayMenuItem.NestedMenu(
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
            add(
                TiwiOverlayMenuItem.NestedMenu(
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
    }

    add(
        TiwiOverlayMenuItem.NestedMenu(
            id = "screensaver_settings",
            label = TextValue.StringResource(R.string.common_screensaver_settings),
            value = null,
            items = nestedItems,
        ),
    )
}
