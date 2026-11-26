package com.pointlessapps.filman.ui.player

import android.util.Log
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.OpenSubtitleCandidate
import com.pointlessapps.filman.data.api.PolishSubtitleSearchResult
import com.pointlessapps.filman.data.api.RawSubtitleLoadResult
import com.pointlessapps.filman.data.api.SubtitleRepository
import com.pointlessapps.filman.data.api.detectSourceType
import com.pointlessapps.filman.data.api.extractReleaseGroup
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.player.lektor.alignment.SubtitleTrackAligner
import com.pointlessapps.filman.player.lektor.parser.SrtParser
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal class PlayerSubtitleDelegate(
    val subtitleRepository: SubtitleRepository,
    private val stateProvider: () -> PlayerState,
    private val updateState: ((PlayerState) -> PlayerState) -> Unit,
    private val sendEffect: (PlayerEffect) -> Unit,
    private val updateTvShowSettings: ((TvShowSourceSettings) -> TvShowSourceSettings) -> Unit,
    private val refreshOverlayMenu: (String?) -> Unit,
    private val launchWork: (
        context: CoroutineContext,
        onError: ((Throwable) -> Unit)?,
        block: suspend CoroutineScope.() -> Unit,
    ) -> Job,
) {
    private var preferredSubtitleLanguage: String? = null
    private var preferredSubtitleLabel: String? = null
    private var preferredAudioLanguage: String? = null
    private var lastAutoAlignedKey: String? = null

    fun onAudioTracksChanged(tracks: List<PlayerAudioTrack>) {
        val currentState = stateProvider()
        val preferredTrack =
            if (currentState.selectedAudioTrackId == null && preferredAudioLanguage != null) {
                tracks.find { it.language.equals(preferredAudioLanguage, ignoreCase = true) }
            } else {
                null
            }
        val selectedId =
            currentState.selectedAudioTrackId?.takeIf { id -> tracks.any { it.id == id } }
                ?: preferredTrack?.id

        updateState {
            it.copy(
                audioTracks = tracks,
                selectedAudioTrackId = selectedId,
            )
        }
    }

    fun selectAudioTrack(trackId: String) {
        val selectedTrack = stateProvider().audioTracks.find { it.id == trackId }
        preferredAudioLanguage = selectedTrack?.language
        updateState { it.copy(selectedAudioTrackId = trackId) }
    }

    fun setSubtitleOffset(offsetMs: Int) {
        updateState { it.copy(subtitleOffsetMs = offsetMs) }
        updateTvShowSettings { it.copy(subtitleOffsetMs = offsetMs) }
        refreshOverlayMenu(null)
    }

    fun adjustSubtitleOffset(deltaMs: Int) {
        val newOffset = stateProvider().subtitleOffsetMs + deltaMs
        setSubtitleOffset(newOffset)
    }

    fun setSubtitleFpsScale(fpsScale: Double) {
        updateState { it.copy(subtitleFpsScale = fpsScale) }
        refreshOverlayMenu(null)
    }

    fun onEmbeddedTracksDiscovered(embeddedSubtitles: List<Subtitle>) {
        if (embeddedSubtitles.isEmpty()) return
        val current = stateProvider()
        val updated = (current.subtitles + embeddedSubtitles).distinctBy { it.url }
        if (updated.size != current.subtitles.size) {
            updateState { it.copy(subtitles = updated) }
            refreshOverlayMenu(null)
        }
    }
    fun selectSubtitle(subtitleUrl: String?) {
        val currentState = stateProvider()
        lastAutoAlignedKey = null
        val selectedSubtitle = (currentState.subtitles + currentState.openSubtitles)
            .find { it.url == subtitleUrl }
        preferredSubtitleLanguage = selectedSubtitle?.language
        preferredSubtitleLabel = selectedSubtitle?.label
        updateState {
            it.copy(
                selectedSubtitleUrl = subtitleUrl,
                rawSubtitleContent = null,
                subtitleOffsetMs = 0,
                subtitleFpsScale = 1.0,
            )
        }
        if (subtitleUrl != null) {
            loadRawSubtitleContent(subtitleUrl)
        } else {
            updateState { it.copy(rawSubtitleContent = null) }
        }
        val videoGroup = extractReleaseGroup(selectedSubtitle?.label.orEmpty())
        val ripSource = detectSourceType(selectedSubtitle?.label.orEmpty())?.name
        updateTvShowSettings {
            it.copy(
                subtitlesEnabled = subtitleUrl != null,
                subtitleLanguage = selectedSubtitle?.language,
                subtitleLabel = selectedSubtitle?.label,
                subtitleOffsetMs = 0,
                preferredSubtitleGroup = videoGroup ?: it.preferredSubtitleGroup,
                preferredRipSource = ripSource ?: it.preferredRipSource,
            )
        }
        refreshOverlayMenu(null)
    }

    fun getPreferredSubtitleUrl(subtitles: List<Subtitle>): String? {
        val lang = preferredSubtitleLanguage ?: return null
        return subtitles.find { it.language == lang && it.label == preferredSubtitleLabel }?.url
            ?: subtitles.find { it.language == lang }?.url
    }

    fun resolveInitialSubtitleUrl(
        subtitles: List<Subtitle>,
        savedSettings: TvShowSourceSettings?,
    ): String? {
        if (savedSettings == null) {
            return getPreferredSubtitleUrl(subtitles)
        }
        return if (savedSettings.subtitlesEnabled) {
            preferredSubtitleLanguage = savedSettings.subtitleLanguage
            preferredSubtitleLabel = savedSettings.subtitleLabel
            subtitleRepository.findMatchingSubtitle(subtitles, savedSettings)
        } else {
            preferredSubtitleLanguage = null
            preferredSubtitleLabel = null
            null
        }
    }

    fun ensurePolishSubtitlesForLektorOrPreference(
        sourceSubtitles: List<Subtitle>,
        selectedSubtitleUrl: String?,
        savedSettings: TvShowSourceSettings? = null,
    ) {
        val currentState = stateProvider()
        val needsPolishSubtitles = currentState.isLektorEnabled ||
            (savedSettings?.subtitlesEnabled == true &&
                (savedSettings.subtitleLanguage?.lowercase() in setOf("pl", "pol") ||
                    savedSettings.subtitleLabel?.contains("pol", ignoreCase = true) == true)) ||
            (savedSettings == null && currentState.selectedSubtitleUrl != null &&
                preferredSubtitleLanguage?.lowercase() in setOf("pl", "pol"))

        if (!needsPolishSubtitles) return

        val allSubs = sourceSubtitles + currentState.openSubtitles
        val currentSub = allSubs.find { it.url == selectedSubtitleUrl }
        if (currentSub == null || !subtitleRepository.isPolishSubtitle(currentSub)) {
            val existingPolish = allSubs.find { subtitleRepository.isPolishSubtitle(it) }
            if (existingPolish != null) {
                selectSubtitle(existingPolish.url)
            } else {
                downloadPolishSubtitles(savedSettings)
            }
        } else if (currentState.rawSubtitleContent == null && selectedSubtitleUrl != null) {
            loadRawSubtitleContent(selectedSubtitleUrl)
        }
    }

    fun onLektorEnabled() {
        val currentState = stateProvider()
        val allSubs = currentState.subtitles + currentState.openSubtitles
        val currentSub = allSubs.find { it.url == currentState.selectedSubtitleUrl }
        if (currentSub == null || !subtitleRepository.isPolishSubtitle(currentSub)) {
            val existingPolish = allSubs.find { subtitleRepository.isPolishSubtitle(it) }
            if (existingPolish != null) {
                selectSubtitle(existingPolish.url)
            } else {
                downloadPolishSubtitles()
            }
        } else if (currentState.rawSubtitleContent == null && currentState.selectedSubtitleUrl != null) {
            loadRawSubtitleContent(currentState.selectedSubtitleUrl)
        } else if (currentState.subtitleOffsetMs == 0) {
            autoAlignCurrentSubtitles(silent = true)
        }
    }

    fun downloadPolishSubtitles(savedSettings: TvShowSourceSettings? = null) {
        val detailedMedia = stateProvider().detailedMedia ?: return
        val currentSource = stateProvider().alternativeSources.find { it.url == stateProvider().videoUrl }
        launchWork(EmptyCoroutineContext, null) {
            val result = subtitleRepository.findBestPolishSubtitle(
                detailedMedia = detailedMedia,
                videoUrl = stateProvider().videoUrl,
                videoHeaders = stateProvider().videoHeaders,
                durationMs = stateProvider().duration.takeIf { it > 0 } ?: (detailedMedia.metaInfo?.duration?.inWholeMilliseconds ?: 0L),
                serverName = currentSource?.serverName,
                quality = currentSource?.quality,
                filename = currentSource?.filename,
                preferredGroup = savedSettings?.preferredSubtitleGroup,
                preferredSource = savedSettings?.preferredRipSource,
                isPossiblyIncomplete = { body, duration ->
                    diagnoseSubtitle(body, duration) != null || isLowQualityPolishSubtitle(body)
                },
            )
            when (result) {
                is PolishSubtitleSearchResult.Found -> {
                    val subtitle = result.subtitle
                    updateState {
                        it.copy(
                            openSubtitles = (it.openSubtitles + subtitle).distinctBy { sub -> sub.url },
                            selectedSubtitleUrl = subtitle.url,
                            rawSubtitleContent = null,
                        )
                    }
                    loadRawSubtitleContent(subtitle.url)
                }

                is PolishSubtitleSearchResult.NotFound -> {
                    val msgRes = if (result.searchFailed) {
                        R.string.player_subtitle_search_failed
                    } else {
                        R.string.player_lektor_subtitles_not_found
                    }
                    sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(msgRes)))
                }
            }
        }
    }

    fun searchSubtitles(language: String) {
        val detailedMedia = stateProvider().detailedMedia ?: return
        launchWork(
            EmptyCoroutineContext,
            { error ->
                Log.e("Subtitles", "Subtitle search failed", error)
                sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_search_failed)))
            },
        ) {
            val currentSource = stateProvider().alternativeSources.find { it.url == stateProvider().videoUrl }
            val (allCandidates, openSubs) = subtitleRepository.searchCandidates(
                detailedMedia = detailedMedia,
                language = language,
                videoUrl = stateProvider().videoUrl,
                videoHeaders = stateProvider().videoHeaders,
                serverName = currentSource?.serverName,
                quality = currentSource?.quality,
                filename = currentSource?.filename,
            )

            if (stateProvider().detailedMedia != detailedMedia) return@launchWork
            updateState {
                it.copy(
                    subtitleCandidates = allCandidates,
                    openSubtitleCandidates = openSubs,
                )
            }

            if (allCandidates.isNotEmpty()) {
                refreshOverlayMenu("menu_subtitle_results")
            } else {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(R.string.player_no_subtitles_found, language),
                    ),
                )
            }
        }
    }

    fun downloadSubtitle(candidate: SubtitleCandidate) {
        val detailedMedia = stateProvider().detailedMedia ?: return
        launchWork(
            EmptyCoroutineContext,
            { error ->
                Log.e("Subtitles", "Subtitle download failed", error)
                sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_download_failed)))
            },
        ) {
            val subtitle = subtitleRepository.downloadCandidate(candidate) ?: return@launchWork
            if (stateProvider().detailedMedia != detailedMedia) return@launchWork
            updateState {
                it.copy(openSubtitles = (it.openSubtitles + subtitle).distinctBy { sub -> sub.url })
            }
            selectSubtitle(subtitle.url)
        }
    }

    fun downloadOpenSubtitle(candidate: OpenSubtitleCandidate) {
        val detailedMedia = stateProvider().detailedMedia ?: return
        launchWork(
            EmptyCoroutineContext,
            { error ->
                Log.e("Subtitles", "OpenSubtitles download failed", error)
                sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_download_failed)))
            },
        ) {
            val subtitle = subtitleRepository.downloadOpenSubtitle(candidate)
            if (stateProvider().detailedMedia != detailedMedia) return@launchWork
            updateState {
                it.copy(openSubtitles = (it.openSubtitles + subtitle).distinctBy { sub -> sub.url })
            }
            selectSubtitle(subtitle.url)
        }
    }

    fun loadRawSubtitleContent(url: String) {
        launchWork(Dispatchers.IO, null) {
            val currentState = stateProvider()
            val isDownloaded = currentState.openSubtitles.any { it.url == url }
            when (
                val result = subtitleRepository.fetchRawSubtitleContent(
                    url = url,
                    isDownloaded = isDownloaded,
                    videoHeaders = currentState.videoHeaders,
                )
            ) {
                is RawSubtitleLoadResult.Success -> {
                    val body = result.body
                    val latest = stateProvider()
                    if (latest.selectedSubtitleUrl != url) return@launchWork
                    val checkableFormat = url.contains(".srt", ignoreCase = true) ||
                        url.contains(".vtt", ignoreCase = true)
                    if (latest.openSubtitles.any { it.url == url } && checkableFormat) {
                        val message = when (diagnoseSubtitle(body, latest.duration)) {
                            SubtitleWarning.UNREADABLE -> R.string.player_subtitle_unreadable
                            SubtitleWarning.POSSIBLY_INCOMPLETE -> R.string.player_subtitle_incomplete
                            null -> null
                        }
                        if (message != null) {
                            sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(message)))
                        }
                    }
                    if (body.contains("-->")) {
                        updateState {
                            if (it.selectedSubtitleUrl == url) it.copy(rawSubtitleContent = body) else it
                        }
                        if (stateProvider().subtitleOffsetMs == 0) {
                            autoAlignCurrentSubtitles(silent = true)
                        }
                    } else {
                        Log.w("Subtitles", "Subtitle content did not contain timestamps")
                    }
                }

                is RawSubtitleLoadResult.Failed -> {
                    val latest = stateProvider()
                    if (latest.selectedSubtitleUrl == url &&
                        latest.openSubtitles.any { it.url == url }
                    ) {
                        sendEffect(
                            PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_download_failed)),
                        )
                    }
                }
            }
        }
    }

    fun autoAlignCurrentSubtitles(silent: Boolean = false) {
        val currentState = stateProvider()
        val detailedMedia = currentState.detailedMedia ?: return
        val currentSubUrl = currentState.selectedSubtitleUrl
        if (currentSubUrl == null) {
            if (!silent) {
                sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitles_off)))
            }
            return
        }

        val alignKey = "${currentState.videoUrl}_$currentSubUrl"
        if (silent && lastAutoAlignedKey == alignKey) {
            return
        }

        updateState { it.copy(isAligningSubtitles = true) }
        refreshOverlayMenu(null)
        if (!silent) {
            sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_auto_align_running)))
        }

        launchWork(
            EmptyCoroutineContext,
            { error ->
                Log.e("Subtitles", "Auto-alignment failed", error)
                updateState { it.copy(isAligningSubtitles = false) }
                refreshOverlayMenu(null)
                if (!silent) {
                    sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_failed)))
                }
            },
        ) {
            try {
                // Ensure current raw subtitle is loaded
                var currentRaw = stateProvider().rawSubtitleContent
                if (currentRaw.isNullOrBlank() || !currentRaw.contains("-->")) {
                    val isDownloaded = stateProvider().openSubtitles.any { it.url == currentSubUrl }
                    val loadResult = subtitleRepository.fetchRawSubtitleContent(
                        url = currentSubUrl,
                        isDownloaded = isDownloaded,
                        videoHeaders = stateProvider().videoHeaders,
                    )
                    if (loadResult is RawSubtitleLoadResult.Success && loadResult.body.contains("-->")) {
                        currentRaw = loadResult.body
                        updateState { it.copy(rawSubtitleContent = currentRaw) }
                    }
                }

                if (currentRaw.isNullOrBlank() || !currentRaw.contains("-->")) {
                    if (!silent) {
                        sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_failed)))
                    }
                    return@launchWork
                }

                // 1. Try finding an alternative track in the video's stream subtitles (NEVER from openSubtitles)
                val streamAlternatives = currentState.subtitles.filter { it.url != currentSubUrl }
                val preferredRef = streamAlternatives.find {
                    it.language.lowercase() in setOf("en", "eng") ||
                        it.label.contains("ang", ignoreCase = true) ||
                        it.label.contains("eng", ignoreCase = true)
                } ?: streamAlternatives.firstOrNull()

                var refBody: String? = null
                if (preferredRef != null) {
                    val refResult = subtitleRepository.fetchRawSubtitleContent(
                        url = preferredRef.url,
                        isDownloaded = false,
                        videoHeaders = currentState.videoHeaders,
                    )
                    refBody = (refResult as? RawSubtitleLoadResult.Success)?.body
                }

                // 2. If no stream reference track, fetch English reference from OpenSubtitles for the video release
                if (refBody.isNullOrBlank() || !refBody.contains("-->")) {
                    val currentSource = currentState.alternativeSources.find { it.url == currentState.videoUrl }
                    val currentSub = (currentState.subtitles + currentState.openSubtitles).find { it.url == currentSubUrl }
                    refBody = subtitleRepository.findReferenceSubtitleForAlignment(
                        detailedMedia = detailedMedia,
                        videoUrl = currentState.videoUrl,
                        videoHeaders = currentState.videoHeaders,
                        serverName = currentSource?.serverName,
                        quality = currentSource?.quality,
                        filename = currentSource?.filename,
                        durationMs = currentState.duration.takeIf { it > 0 } ?: (detailedMedia.metaInfo?.duration?.inWholeMilliseconds ?: 0L),
                        currentTargetSubtitleLabel = currentSub?.label,
                    )
                }

                if (refBody.isNullOrBlank() || !refBody.contains("-->")) {
                    if (!silent) {
                        sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_low_confidence)))
                    }
                    return@launchWork
                }

                val refCues = SrtParser.parse(refBody, normalizePolish = false)
                val currentCues = SrtParser.parse(currentRaw, normalizePolish = false)
                val alignment = SubtitleTrackAligner.align(referenceCues = refCues, targetCues = currentCues)

                if (alignment == null || alignment.confidence < 0.2) {
                    if (!silent) {
                        sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_low_confidence)))
                    }
                    return@launchWork
                }

                lastAutoAlignedKey = alignKey
                setSubtitleOffset(alignment.bestOffsetMs)
                setSubtitleFpsScale(alignment.bestFpsRatio)
                val offsetSec = String.format(Locale.US, "%.2f", alignment.bestOffsetMs / 1000.0)
                val scaleStr = String.format(Locale.US, "%.3f", alignment.bestFpsRatio)
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(
                            R.string.player_subtitle_aligned_success,
                            offsetSec,
                            scaleStr,
                        ),
                    ),
                )
            } finally {
                updateState { it.copy(isAligningSubtitles = false) }
                refreshOverlayMenu(null)
            }
        }
    }

    fun autoAlignSubtitles(referenceSubtitleUrl: String) {
        val currentState = stateProvider()
        val currentSubUrl = currentState.selectedSubtitleUrl ?: return
        updateState { it.copy(isAligningSubtitles = true) }
        refreshOverlayMenu(null)
        sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_auto_align_running)))

        launchWork(
            EmptyCoroutineContext,
            { error ->
                Log.e("Subtitles", "Subtitle alignment failed", error)
                updateState { it.copy(isAligningSubtitles = false) }
                refreshOverlayMenu(null)
                sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_failed)))
            },
        ) {
            try {
                var currentRaw = stateProvider().rawSubtitleContent
                if (currentRaw.isNullOrBlank() || !currentRaw.contains("-->")) {
                    val isDownloaded = stateProvider().openSubtitles.any { it.url == currentSubUrl }
                    val loadResult = subtitleRepository.fetchRawSubtitleContent(
                        url = currentSubUrl,
                        isDownloaded = isDownloaded,
                        videoHeaders = stateProvider().videoHeaders,
                    )
                    if (loadResult is RawSubtitleLoadResult.Success && loadResult.body.contains("-->")) {
                        currentRaw = loadResult.body
                        updateState { it.copy(rawSubtitleContent = currentRaw) }
                    }
                }

                if (currentRaw.isNullOrBlank() || !currentRaw.contains("-->")) {
                    sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_failed)))
                    return@launchWork
                }

                val isDownloaded = currentState.openSubtitles.any { it.url == referenceSubtitleUrl }
                val refResult = subtitleRepository.fetchRawSubtitleContent(
                    url = referenceSubtitleUrl,
                    isDownloaded = isDownloaded,
                    videoHeaders = currentState.videoHeaders,
                )
                val refBody = (refResult as? RawSubtitleLoadResult.Success)?.body
                if (refBody.isNullOrBlank() || !refBody.contains("-->")) {
                    sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_failed)))
                    return@launchWork
                }

                val refCues = SrtParser.parse(refBody, normalizePolish = false)
                val currentCues = SrtParser.parse(currentRaw, normalizePolish = false)
                val alignment = SubtitleTrackAligner.align(referenceCues = refCues, targetCues = currentCues)

                if (alignment == null || alignment.confidence < 0.2) {
                    sendEffect(PlayerEffect.ShowToast(TextValue.StringResource(R.string.player_subtitle_alignment_low_confidence)))
                    return@launchWork
                }

                setSubtitleOffset(alignment.bestOffsetMs)
                setSubtitleFpsScale(alignment.bestFpsRatio)
                val offsetSec = String.format(Locale.US, "%.2f", alignment.bestOffsetMs / 1000.0)
                val scaleStr = String.format(Locale.US, "%.3f", alignment.bestFpsRatio)
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(
                            R.string.player_subtitle_aligned_success,
                            offsetSec,
                            scaleStr,
                        ),
                    ),
                )
            } finally {
                updateState { it.copy(isAligningSubtitles = false) }
                refreshOverlayMenu(null)
            }
        }
    }
}
