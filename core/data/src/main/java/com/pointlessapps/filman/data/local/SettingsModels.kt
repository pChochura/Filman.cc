package com.pointlessapps.filman.data.local

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.data.model.LektorTiming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Immutable
data class LektorSettingsPreferences(
    val isEnabled: Boolean = false,
    val hideSubtitles: Boolean = false,
    val voiceId: String = "edge_marek",
    val duckingVolume: Float = LektorDucking.DEFAULT_VOLUME,
    val timingOffsetMs: Int = LektorTiming.DEFAULT_OFFSET_MS,
    val edgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
    val edgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
    val edgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
)

@Immutable
data class NextEpisodeSettingsPreferences(
    val autoPlayNext: Boolean = true,
    val initialType: NextEpisodeAppearance = NextEpisodeAppearance.SHOW_IN_OVERLAY,
    val initialOffsetSec: Long = 100L,
    val initialPercentage: Long = 5L,
    val secondaryType: NextEpisodeAppearance = NextEpisodeAppearance.SHOW_WITH_TIMER,
    val secondaryOffsetSec: Long = 30L,
    val secondaryPercentage: Long = 2L,
    val secondaryTimerSec: Long = 10L,
)

@Immutable
data class ScreensaverSettingsPreferences(
    val isEnabled: Boolean = true,
    val inactivityTimeMs: Long = 120_000L,
    val slideDurationMs: Long = 10_000L,
)

fun SettingsManager.lektorPreferencesFlow(): Flow<LektorSettingsPreferences> {
    val coreFlow = combine(
        isLektorEnabled,
        lektorHideSubtitles,
        lektorVoiceId,
        lektorDuckingVolume,
        lektorTimingOffsetMs,
    ) { enabled, hideSubs, voiceId, ducking, timing ->
        LektorSettingsPreferences(
            isEnabled = enabled,
            hideSubtitles = hideSubs,
            voiceId = voiceId,
            duckingVolume = ducking,
            timingOffsetMs = timing,
        )
    }
    return combine(
        coreFlow,
        lektorEdgeRate,
        lektorEdgePitch,
        lektorEdgeVolume,
    ) { core, rate, pitch, volume ->
        core.copy(
            edgeRate = rate,
            edgePitch = pitch,
            edgeVolume = volume,
        )
    }
}

fun SettingsManager.nextEpisodePreferencesFlow(): Flow<NextEpisodeSettingsPreferences> {
    val initialFlow = combine(
        autoPlayNextFlow,
        initialAppearanceTypeFlow,
        initialAppearanceOffsetFlow,
        initialAppearancePercentageFlow,
    ) { autoPlay, type, offset, pct ->
        NextEpisodeSettingsPreferences(
            autoPlayNext = autoPlay,
            initialType = type,
            initialOffsetSec = offset,
            initialPercentage = pct,
        )
    }
    return combine(
        initialFlow,
        secondaryAppearanceTypeFlow,
        secondaryAppearanceOffsetFlow,
        secondaryAppearancePercentageFlow,
        secondaryTimerAmountFlow,
    ) { base, secType, secOffset, secPct, secTimer ->
        base.copy(
            secondaryType = secType,
            secondaryOffsetSec = secOffset,
            secondaryPercentage = secPct,
            secondaryTimerSec = secTimer,
        )
    }
}

fun SettingsManager.screensaverPreferencesFlow(): Flow<ScreensaverSettingsPreferences> = combine(
    isScreensaverEnabled,
    screensaverInactivityTime,
    screensaverSlideDuration,
) { enabled, inactivity, slide ->
    ScreensaverSettingsPreferences(
        isEnabled = enabled,
        inactivityTimeMs = inactivity,
        slideDurationMs = slide,
    )
}
