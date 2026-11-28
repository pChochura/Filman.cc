package com.pointlessapps.filman.ui.player

import android.util.Log
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.HIDE
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_WITH_TIMER
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.Show
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowInOverlay
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowWithTimer
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal class PlayerNextEpisodeDelegate(
    private val settingsManager: SettingsManager,
    private val videoUrlResolver: VideoUrlResolver,
    private val stateProvider: () -> PlayerState,
    private val launchPrefetch: (onError: (Throwable) -> Unit, suspend CoroutineScope.() -> Unit) -> Job,
) {
    private var nextEpisodeButtonModel: NextEpisodeButtonModel = NextEpisodeButtonModel()
    private var prefetchedNextEpisodeUrl: String? = null
    private var prefetchJob: Job? = null

    private var cachedRawContent: String? = null
    private var cachedOffsetMs: Int = 0
    private var cachedFpsScale: Double = 1.0
    private var cachedTimeline: com.pointlessapps.filman.player.lektor.model.SubtitleTimeline? = null

    private fun getTimeline(state: PlayerState): com.pointlessapps.filman.player.lektor.model.SubtitleTimeline? {
        val raw = state.rawSubtitleContent ?: return null
        if (raw == cachedRawContent &&
            state.subtitleOffsetMs == cachedOffsetMs &&
            state.subtitleFpsScale == cachedFpsScale
        ) {
            return cachedTimeline
        }
        cachedRawContent = raw
        cachedOffsetMs = state.subtitleOffsetMs
        cachedFpsScale = state.subtitleFpsScale
        val cues = com.pointlessapps.filman.player.lektor.parser.SrtParser.parse(raw, normalizePolish = false)
        cachedTimeline = com.pointlessapps.filman.player.lektor.model.SubtitleTimeline(
            cues = cues,
            offsetMs = state.subtitleOffsetMs,
            fpsScale = state.subtitleFpsScale,
        )
        return cachedTimeline
    }

    fun observeUiState(stateFlow: StateFlow<PlayerState>): Flow<NextEpisodeButtonUIState> {
        val initialModelFlow = combine(
            settingsManager.initialAppearanceTypeFlow,
            settingsManager.initialAppearanceOffsetFlow,
            settingsManager.initialAppearancePercentageFlow,
        ) { type, offset, percentage ->
            val percentageOffset = percentage / 100f
            val maxTimeOffset = offset * 1000L
            when (type) {
                SHOW -> Show(percentageOffset, maxTimeOffset)
                SHOW_IN_OVERLAY -> ShowInOverlay(percentageOffset, maxTimeOffset)
                else -> null
            }
        }

        val secondaryModelFlow = combine(
            settingsManager.secondaryAppearanceTypeFlow,
            settingsManager.secondaryAppearanceOffsetFlow,
            settingsManager.secondaryAppearancePercentageFlow,
            settingsManager.secondaryTimerAmountFlow,
        ) { type, offset, percentage, timerAmount ->
            val percentageOffset = percentage / 100f
            val maxTimeOffset = offset * 1000L
            when (type) {
                SHOW -> Show(percentageOffset, maxTimeOffset)
                SHOW_IN_OVERLAY -> ShowInOverlay(percentageOffset, maxTimeOffset)
                SHOW_WITH_TIMER -> ShowWithTimer(percentageOffset, maxTimeOffset, timerAmount * 1000L)
                HIDE -> null
            }
        }

        val baseModelFlow = combine(initialModelFlow, secondaryModelFlow) { initial, secondary ->
            NextEpisodeButtonModel(initial, secondary).also {
                nextEpisodeButtonModel = it
            }
        }

        return combine(baseModelFlow, stateFlow) { model, currentState ->
            computeUiState(model, currentState)
        }
    }

    private fun computeUiState(
        model: NextEpisodeButtonModel,
        currentState: PlayerState,
    ): NextEpisodeButtonUIState {
        val duration = currentState.duration
        val currentPosition = currentState.currentPositionMs
        val areControlsVisible = currentState.areControlsVisible

        val timeline = getTimeline(currentState)
        val detectedCreditsStart = timeline?.detectDialogueEndMs(duration)
        val isSpokenDialogueActive = timeline?.hasSpokenDialogueAt(currentPosition) == true

        fun isPastThreshold(appearance: AppearanceModel?, isSecondary: Boolean): Boolean {
            if (appearance == null || duration <= 0) return false
            val standardThreshold = duration - minOf(
                appearance.maxTimeOffset,
                (duration * appearance.percentageOffset).toLong(),
            )
            val effectiveThreshold = if (detectedCreditsStart != null) {
                if (isSecondary) {
                    maxOf(detectedCreditsStart + 5_000L, standardThreshold).coerceAtMost(duration - 2_000L)
                } else {
                    detectedCreditsStart
                }
            } else {
                standardThreshold
            }
            return currentPosition >= effectiveThreshold
        }

        val isInitialPhase = isPastThreshold(model.initialAppearanceModel, isSecondary = false) && !isSpokenDialogueActive
        val isSecondaryPhase = isPastThreshold(model.appearanceModel, isSecondary = true) && !isSpokenDialogueActive
        val activeModel = when {
            isSecondaryPhase -> model.appearanceModel
            isInitialPhase -> model.initialAppearanceModel
            else -> null
        }

        var isVisible = false
        var shouldRunTimer = false
        val autoPlayNextEpisode = settingsManager.autoPlayNextFlow.value

        when (activeModel) {
            is Show -> isVisible = true
            is ShowInOverlay -> isVisible = areControlsVisible
            is ShowWithTimer -> {
                isVisible = true
                shouldRunTimer = autoPlayNextEpisode && !areControlsVisible && !currentState.isTimerCancelled
            }
            null -> isVisible = false
        }

        if (isSecondaryPhase && currentState.isSecondaryPhaseDismissed) {
            isVisible = false
            shouldRunTimer = false
        } else if (isInitialPhase && !isSecondaryPhase && currentState.isInitialPhaseDismissed) {
            isVisible = false
            shouldRunTimer = false
        }

        if (isVisible) {
            val nextUrl = currentState.detailedMedia?.baseItem?.nextEpisodeUrl
            if (nextUrl != null) {
                prefetchNextEpisode(nextUrl)
            }
        }

        return NextEpisodeButtonUIState(
            isVisible = isVisible,
            isSecondaryPhase = isSecondaryPhase,
            isTimerRunning = shouldRunTimer,
            timerDurationMs = (activeModel as? ShowWithTimer)?.timerDuration ?: 0L,
        )
    }

    fun checkPrefetchNextEpisode(currentPositionMs: Long, duration: Long) {
        val detailedMedia = stateProvider().detailedMedia ?: return
        if (!detailedMedia.baseItem.isTvShow) return
        val nextEpisodeUrl = detailedMedia.baseItem.nextEpisodeUrl ?: return
        if (prefetchedNextEpisodeUrl == nextEpisodeUrl) return
        if (duration <= 0L || currentPositionMs <= 0L) return

        fun calculateThreshold(appearance: AppearanceModel?): Long? {
            if (appearance == null) return null
            return duration - minOf(
                appearance.maxTimeOffset,
                (duration * appearance.percentageOffset).toLong(),
            )
        }

        val timeline = getTimeline(stateProvider())
        val detectedCreditsStart = timeline?.detectDialogueEndMs(duration)

        val earliestButtonThreshold = listOfNotNull(
            calculateThreshold(nextEpisodeButtonModel.initialAppearanceModel),
            calculateThreshold(nextEpisodeButtonModel.appearanceModel),
            detectedCreditsStart,
        ).minOrNull()

        val standardThreshold = if (duration > PlayerViewModel.PREFETCH_LEAD_TIME_MS * 2) {
            duration - PlayerViewModel.PREFETCH_LEAD_TIME_MS
        } else {
            duration / 2
        }

        val prefetchThreshold = if (earliestButtonThreshold != null) {
            val beforeButton = earliestButtonThreshold - PlayerViewModel.MIN_PREFETCH_BEFORE_BUTTON_MS
            minOf(standardThreshold, beforeButton).coerceAtLeast(duration / 2)
        } else {
            standardThreshold
        }

        if (currentPositionMs >= prefetchThreshold) {
            prefetchNextEpisode(nextEpisodeUrl)
        }
    }

    private fun prefetchNextEpisode(nextEpisodeUrl: String) {
        if (prefetchedNextEpisodeUrl == nextEpisodeUrl) return
        prefetchedNextEpisodeUrl = nextEpisodeUrl

        prefetchJob?.cancel()
        prefetchJob = launchPrefetch(
            { t ->
                runCatching { Log.w("PlayerViewModel", "Failed to prefetch next episode sources: $nextEpisodeUrl", t) }
            },
        ) {
            runCatching { Log.d("PlayerViewModel", "Prefetching next episode sources for $nextEpisodeUrl") }
            videoUrlResolver.prefetch(nextEpisodeUrl)
        }
    }

    fun resetForNewMedia() {
        prefetchJob?.cancel()
        prefetchedNextEpisodeUrl = null
    }

    fun clear() {
        prefetchJob?.cancel()
    }
}
