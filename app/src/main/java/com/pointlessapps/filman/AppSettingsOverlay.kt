package com.pointlessapps.filman

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.ui.components.FilmanOverlayClickScope
import com.pointlessapps.filman.ui.components.FilmanOverlayMenu
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.core.TextValue

@Composable
internal fun AppOverlayMenu(
    isLoggedIn: Boolean,
    loggedInUsername: String? = null,
    preferredQuality: String,
    autoPlayNextEpisode: Boolean,
    appVersion: String,
    initialAppearanceType: NextEpisodeAppearance,
    initialAppearanceOffset: Long,
    secondaryAppearanceType: NextEpisodeAppearance,
    secondaryAppearanceOffset: Long,
    secondaryTimerAmount: Long,
    initialAppearancePercentage: Long,
    secondaryAppearancePercentage: Long,
    isScreensaverEnabled: Boolean,
    screensaverInactivityTime: Long,
    screensaverSlideDuration: Long,
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
    onDismissRequest: () -> Unit,
    onInitialAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onInitialAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryAppearanceTypeToggled: (NextEpisodeAppearance) -> Unit,
    onSecondaryAppearanceOffsetToggled: (Long) -> Unit,
    onSecondaryTimerAmountToggled: (Long) -> Unit,
    onInitialAppearancePercentageToggled: (Long) -> Unit,
    onSecondaryAppearancePercentageToggled: (Long) -> Unit,
    onScreensaverEnabledToggled: (Boolean) -> Unit,
    onScreensaverInactivityTimeChanged: (Long) -> Unit,
    onScreensaverSlideDurationChanged: (Long) -> Unit,
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
    onLogoutClicked: () -> Unit,
    onSyncNowClicked: () -> Unit = {},
    onLoginClicked: () -> Unit,
    onPreferredQualitySelected: (String) -> Unit,
    onAutoPlayNextEpisodeToggled: (Boolean) -> Unit,
    onWatchHistoryClicked: FilmanOverlayClickScope.() -> Unit,
    onFavoritesClicked: FilmanOverlayClickScope.() -> Unit,
) {
    val items = mutableListOf<FilmanOverlayMenuItem>()

    items.addAll(
        getPlaybackSettings(
            preferredQuality = preferredQuality,
            autoPlayNextEpisode = autoPlayNextEpisode,
            initialAppearanceType = initialAppearanceType,
            initialAppearanceOffset = initialAppearanceOffset,
            secondaryAppearanceType = secondaryAppearanceType,
            secondaryAppearanceOffset = secondaryAppearanceOffset,
            secondaryTimerAmount = secondaryTimerAmount,
            initialAppearancePercentage = initialAppearancePercentage,
            secondaryAppearancePercentage = secondaryAppearancePercentage,
            isLektorEnabled = isLektorEnabled,
            lektorHideSubtitles = lektorHideSubtitles,
            lektorVoiceId = lektorVoiceId,
            lektorDuckingVolume = lektorDuckingVolume,
            lektorTimingOffsetMs = lektorTimingOffsetMs,
            lektorEdgeRate = lektorEdgeRate,
            lektorEdgePitch = lektorEdgePitch,
            lektorEdgeVolume = lektorEdgeVolume,
            lektorEdgeCalmMode = lektorEdgeCalmMode,
            downloadedLektorVoices = downloadedLektorVoices,
            lektorModelDownloadProgress = lektorModelDownloadProgress,
            isLektorModelExtracting = isLektorModelExtracting,
            systemVoices = systemVoices,
            onInitialAppearanceTypeToggled = onInitialAppearanceTypeToggled,
            onInitialAppearanceOffsetToggled = onInitialAppearanceOffsetToggled,
            onSecondaryAppearanceTypeToggled = onSecondaryAppearanceTypeToggled,
            onSecondaryAppearanceOffsetToggled = onSecondaryAppearanceOffsetToggled,
            onSecondaryTimerAmountToggled = onSecondaryTimerAmountToggled,
            onInitialAppearancePercentageToggled = onInitialAppearancePercentageToggled,
            onSecondaryAppearancePercentageToggled = onSecondaryAppearancePercentageToggled,
            onPreferredQualitySelected = onPreferredQualitySelected,
            onAutoPlayNextEpisodeToggled = onAutoPlayNextEpisodeToggled,
            onToggleLektor = onToggleLektor,
            onSetLektorHideSubtitles = onSetLektorHideSubtitles,
            onSelectLektorVoice = onSelectLektorVoice,
            onChangeLektorDuckingVolume = onChangeLektorDuckingVolume,
            onChangeLektorTimingOffset = onChangeLektorTimingOffset,
            onChangeLektorEdgeRate = onChangeLektorEdgeRate,
            onChangeLektorEdgePitch = onChangeLektorEdgePitch,
            onChangeLektorEdgeVolume = onChangeLektorEdgeVolume,
            onChangeLektorEdgeCalmMode = onChangeLektorEdgeCalmMode,
            onDownloadLektorModel = onDownloadLektorModel,
            onDeleteLektorModel = onDeleteLektorModel,
            onPreviewSystemVoice = onPreviewSystemVoice,
            onRefreshSystemVoices = onRefreshSystemVoices,
            onTestVoice = onTestVoice,
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Header(
            id = "library_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_library),
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Button(
            id = "watch_history",
            label = TextValue.StringResource(R.string.overlay_menu_watch_history),
            onClick = onWatchHistoryClicked,
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Button(
            id = "favorites",
            label = TextValue.StringResource(R.string.overlay_menu_favorites),
            onClick = onFavoritesClicked,
        ),
    )

    items.add(
        FilmanOverlayMenuItem.Header(
            id = "other_header",
            label = TextValue.StringResource(R.string.overlay_menu_header_other),
        ),
    )

    items.buildScreensaverSettings(
        isScreensaverEnabled = isScreensaverEnabled,
        screensaverInactivityTime = screensaverInactivityTime,
        screensaverSlideDuration = screensaverSlideDuration,
        onScreensaverEnabledToggled = onScreensaverEnabledToggled,
        onScreensaverInactivityTimeChanged = onScreensaverInactivityTimeChanged,
        onScreensaverSlideDurationChanged = onScreensaverSlideDurationChanged,
    )

    if (isLoggedIn) {
        val userLabel = if (!loggedInUsername.isNullOrBlank()) {
            TextValue.StringResource(R.string.overlay_menu_logged_in_as, loggedInUsername)
        } else {
            TextValue.StringResource(R.string.overlay_menu_account)
        }
        items.add(
            FilmanOverlayMenuItem.Button(
                id = "sync_now",
                label = TextValue.StringResource(R.string.overlay_menu_sync_now),
                onClick = { onSyncNowClicked() },
            ),
        )
        items.add(
            confirmActionNestedMenu(
                id = "logout",
                labelRes = R.string.overlay_menu_logout,
                value = userLabel,
                onConfirm = onLogoutClicked,
            ),
        )
    } else {
        items.add(
            FilmanOverlayMenuItem.Button(
                id = "login",
                label = TextValue.StringResource(R.string.overlay_menu_login),
                onClick = { onLoginClicked() },
            ),
        )
    }

    items.add(
        FilmanOverlayMenuItem.Footer(
            id = "footer_version",
            label = TextValue.StringResource(R.string.overlay_menu_app_version, appVersion),
        ),
    )

    FilmanOverlayMenu(
        title = TextValue.StringResource(R.string.overlay_menu_settings),
        onDismissRequest = onDismissRequest,
        items = items,
    )
}

private fun confirmActionNestedMenu(
    id: String,
    @StringRes labelRes: Int,
    value: TextValue? = null,
    onConfirm: () -> Unit,
): FilmanOverlayMenuItem.NestedMenu = FilmanOverlayMenuItem.NestedMenu(
    id = id,
    label = TextValue.StringResource(labelRes),
    value = value,
    items = listOf(
        FilmanOverlayMenuItem.Header(
            id = "${id}_header",
            label = TextValue.StringResource(R.string.overlay_menu_are_you_sure),
        ),
        FilmanOverlayMenuItem.Button(
            id = "${id}_yes",
            label = TextValue.StringResource(R.string.overlay_menu_yes),
            onClick = {
                onConfirm()
                popBack()
            },
        ),
        FilmanOverlayMenuItem.Button(
            id = "${id}_no",
            label = TextValue.StringResource(R.string.overlay_menu_no),
            onClick = { popBack() },
        ),
    ),
)
