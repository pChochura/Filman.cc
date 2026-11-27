package com.pointlessapps.filman.ui.player

import androidx.compose.runtime.Immutable
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.api.OpenSubtitleCandidate
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.core.PlayerConstants
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState

sealed interface PlayerEvent : TiwiEvent {
    data class UpdateSubtitleStyle(val preferences: SubtitleStylePreferences) : PlayerEvent

    data class LoadDetails(
        val url: String,
        val title: String? = null,
        val description: String? = null,
        val isTrailer: Boolean = false,
    ) : PlayerEvent

    data class IsPlayingChanged(
        val isPlaying: Boolean,
    ) : PlayerEvent

    data class IsBufferingChanged(
        val isBuffering: Boolean,
    ) : PlayerEvent

    data class DurationProvided(
        val duration: Long,
    ) : PlayerEvent

    data object NextEpisodeRequested : PlayerEvent

    data class SaveProgress(
        val url: String,
        val positionMs: Long,
    ) : PlayerEvent

    data class OpenSettingsMenu(
        val currentPositionMs: Long,
        val initialMenuId: String? = null,
    ) : PlayerEvent

    data class ControlsVisibilityChanged(
        val isVisible: Boolean,
    ) : PlayerEvent

    data class CurrentPositionChanged(
        val positionMs: Long,
    ) : PlayerEvent

    data object NextEpisodePromptDismissed : PlayerEvent

    data object CancelNextEpisodeTimer : PlayerEvent

    data class ChangeVideoSource(
        val source: ExtractedVideo,
    ) : PlayerEvent

    data class AudioTracksChanged(
        val audioTracks: List<PlayerAudioTrack>,
    ) : PlayerEvent

    data class SelectAudioTrack(
        val trackId: String,
    ) : PlayerEvent

    data class SelectSubtitle(
        val subtitleUrl: String?,
    ) : PlayerEvent

    data class SetSubtitleOffset(val offsetMs: Int) : PlayerEvent
    data class AdjustSubtitleOffset(val deltaMs: Int) : PlayerEvent
    data class SetSubtitleFpsScale(val fpsScale: Double) : PlayerEvent
    data object AutoAlignCurrentSubtitles : PlayerEvent
    data class AutoAlignSubtitles(val targetSubtitleUrl: String) : PlayerEvent

    data class SearchSubtitles(
        val language: String,
    ) : PlayerEvent

    data class DownloadSubtitle(
        val candidate: SubtitleCandidate,
    ) : PlayerEvent

    data class SearchOpenSubtitles(
        val language: String,
    ) : PlayerEvent

    data class DownloadOpenSubtitle(
        val candidate: OpenSubtitleCandidate,
    ) : PlayerEvent

    data class SearchWyzieSubtitles(
        val language: String,
    ) : PlayerEvent

    data class ChangePlaybackSpeed(
        val speed: Float,
    ) : PlayerEvent

    data class ChangeAspectRatio(
        val mode: Int,
    ) : PlayerEvent

    data class ToggleLektor(
        val enabled: Boolean,
    ) : PlayerEvent

    data class SetLektorHideSubtitles(val hide: Boolean) : PlayerEvent

    data class SelectLektorVoice(
        val voiceId: String,
    ) : PlayerEvent

    data class PreviewSystemVoice(val name: String?) : PlayerEvent
    data object RefreshSystemVoices : PlayerEvent
    data object TestVoice : PlayerEvent

    data class ChangeLektorDuckingVolume(
        val volume: Float,
    ) : PlayerEvent

    data class ChangeLektorTimingOffset(val offsetMs: Int) : PlayerEvent
    data class SetLektorEdgeRate(val rate: String) : PlayerEvent
    data class SetLektorEdgePitch(val pitch: String) : PlayerEvent
    data class SetLektorEdgeVolume(val volume: String) : PlayerEvent
    data class SetLektorEdgeCalmMode(val enabled: Boolean) : PlayerEvent

    data class DownloadLektorModel(
        val voiceId: String,
    ) : PlayerEvent

    data class DeleteLektorModel(
        val voiceId: String,
    ) : PlayerEvent

    data class SelectWatchMode(
        val mode: WatchMode,
    ) : PlayerEvent

    data object PlayerError : PlayerEvent
}

enum class WatchMode {
    ORIGINAL,
    SUBTITLES,
    LEKTOR,
    DUBBING,
}

@Immutable
data class PlayerState(
    val videoUrl: String? = null,
    val videoHeaders: Map<String, String> = emptyMap(),
    val detailedMedia: DetailedMedia? = null,
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = true,
    val duration: Long = 0,
    val startPositionMs: Long = 0,
    val playbackSpeed: Float = PlayerConstants.PlaybackSpeed.X1_0,
    val aspectRatioMode: Int = PlayerConstants.AspectRatio.FIT,
    val audioTracks: List<PlayerAudioTrack> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val openSubtitles: List<Subtitle> = emptyList(),
    val subtitleCandidates: List<SubtitleCandidate> = emptyList(),
    val openSubtitleCandidates: List<OpenSubtitleCandidate> = emptyList(),
    val selectedSubtitleUrl: String? = null,
    val rawSubtitleContent: String? = null,
    val subtitleOffsetMs: Int = 0,
    val subtitleFpsScale: Double = 1.0,
    val isAligningSubtitles: Boolean = false,
    val isLektorEnabled: Boolean = false,
    val lektorHideSubtitles: Boolean = false,
    val lektorVoiceId: String = "edge_marek",
    val systemVoices: SystemVoiceList = SystemVoiceList(),
    val lektorDuckingVolume: Float = LektorDucking.DEFAULT_VOLUME,
    val lektorTimingOffsetMs: Int = LektorTiming.DEFAULT_OFFSET_MS,
    val lektorEdgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
    val lektorEdgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
    val lektorEdgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    val lektorEdgeCalmMode: Boolean = LektorEdgeVoiceOptions.DEFAULT_CALM_MODE,
    val downloadedLektorVoices: Set<String> = emptySet(),
    val lektorModelDownloadProgress: Float? = null,
    val isLektorModelExtracting: Boolean = false,
    val failedUrls: Set<String> = emptySet(),
    val alternativeSources: List<ExtractedVideo> = emptyList(),
    val isFetchingMoreSources: Boolean = false,
    val currentPositionMs: Long = 0,
    val areControlsVisible: Boolean = false,
    val nextEpisodeButtonUIState: NextEpisodeButtonUIState = NextEpisodeButtonUIState(),
    val isInitialPhaseDismissed: Boolean = false,
    val isSecondaryPhaseDismissed: Boolean = false,
    val isTimerCancelled: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val subtitleStylePreferences: SubtitleStylePreferences = SubtitleStylePreferences(),
    override val shared: SharedState = SharedState(),
) : StateWithShared<PlayerState> {
    val showSubtitles: Boolean get() = !isLektorEnabled || !lektorHideSubtitles

    val currentWatchMode: WatchMode
        get() {
            val activeTrack = audioTracks.find { it.id == selectedAudioTrackId || (selectedAudioTrackId == null && it.isSelected) }
            val activeTrackName = activeTrack?.label?.lowercase().orEmpty()
            val activeTrackLang = activeTrack?.language?.lowercase().orEmpty()
            val isDubbingTrack = activeTrackName.contains("dubbing") || activeTrackLang.contains("dubbing")
            if (isDubbingTrack) return WatchMode.DUBBING

            val isLektorTrack = activeTrackName.contains("lektor") || activeTrackLang.contains("lektor")
            if (isLektorEnabled || isLektorTrack) return WatchMode.LEKTOR

            if (selectedSubtitleUrl != null && showSubtitles) return WatchMode.SUBTITLES

            return WatchMode.ORIGINAL
        }

    val hasDubbing: Boolean
        get() = audioTracks.any { track ->
            val name = track.label.lowercase()
            val lang = track.language?.lowercase().orEmpty()
            name.contains("dubbing") || lang.contains("dubbing")
        }

    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

sealed interface PlayerEffect {
    data object NavigateToAuth : PlayerEffect

    data class ShowToast(
        val message: TextValue,
    ) : PlayerEffect
}
