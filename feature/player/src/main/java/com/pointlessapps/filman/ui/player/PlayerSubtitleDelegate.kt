package com.pointlessapps.filman.ui.player

import android.util.Log
import com.pointlessapps.filman.core.ui.R
import com.pointlessapps.filman.data.api.OpenSubtitleCandidate
import com.pointlessapps.filman.data.api.PolishSubtitleSearchResult
import com.pointlessapps.filman.data.api.RawSubtitleLoadResult
import com.pointlessapps.filman.data.api.SubtitleRepository
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.ui.core.TextValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    fun selectSubtitle(subtitleUrl: String?) {
        val currentState = stateProvider()
        val selectedSubtitle = (currentState.subtitles + currentState.openSubtitles)
            .find { it.url == subtitleUrl }
        preferredSubtitleLanguage = selectedSubtitle?.language
        preferredSubtitleLabel = selectedSubtitle?.label
        updateState { it.copy(selectedSubtitleUrl = subtitleUrl, rawSubtitleContent = null) }
        if (subtitleUrl != null) {
            loadRawSubtitleContent(subtitleUrl)
        } else {
            updateState { it.copy(rawSubtitleContent = null) }
        }
        updateTvShowSettings {
            it.copy(
                subtitlesEnabled = subtitleUrl != null,
                subtitleLanguage = selectedSubtitle?.language,
                subtitleLabel = selectedSubtitle?.label,
            )
        }
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
            (savedSettings == null && preferredSubtitleLanguage?.lowercase() in setOf("pl", "pol"))

        if (!needsPolishSubtitles) return

        val allSubs = sourceSubtitles + currentState.openSubtitles
        val currentSub = allSubs.find { it.url == selectedSubtitleUrl }
        if (currentSub == null || !subtitleRepository.isPolishSubtitle(currentSub)) {
            val existingPolish = allSubs.find { subtitleRepository.isPolishSubtitle(it) }
            if (existingPolish != null) {
                selectSubtitle(existingPolish.url)
            } else {
                downloadPolishSubtitles()
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
        }
    }

    fun downloadPolishSubtitles() {
        val detailedMedia = stateProvider().detailedMedia ?: return
        launchWork(EmptyCoroutineContext, null) {
            val result = subtitleRepository.findBestPolishSubtitle(
                detailedMedia = detailedMedia,
                videoUrl = stateProvider().videoUrl,
                videoHeaders = stateProvider().videoHeaders,
                durationMs = stateProvider().duration,
                isPossiblyIncomplete = { body, duration ->
                    diagnoseSubtitle(body, duration) != null || isLowQualityPolishSubtitle(body)
                },
            )
            when (result) {
                is PolishSubtitleSearchResult.Found -> {
                    val subtitle = result.subtitle
                    updateState {
                        it.copy(
                            openSubtitles = it.openSubtitles + subtitle,
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
            val (allCandidates, openSubs) = subtitleRepository.searchCandidates(
                detailedMedia = detailedMedia,
                language = language,
                videoUrl = stateProvider().videoUrl,
                videoHeaders = stateProvider().videoHeaders,
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
}
