package com.pointlessapps.filman.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.CaptionStyleCompat
import com.pointlessapps.filman.R
import com.pointlessapps.filman.config.EkinoConfig
import com.pointlessapps.filman.config.FilmanConfig
import com.pointlessapps.filman.config.ZaluknijConfig
import com.pointlessapps.filman.data.local.ProgressManager
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.HIDE
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_IN_OVERLAY
import com.pointlessapps.filman.data.local.SettingsConstants.NextEpisodeAppearance.SHOW_WITH_TIMER
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.local.TvShowSettingsManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.ProgressItem
import com.pointlessapps.filman.data.model.SubtitleStylePreferences
import com.pointlessapps.filman.data.model.TvShowSourceSettings
import com.pointlessapps.filman.data.model.getTvShowKey
import com.pointlessapps.filman.data.scraper.FilmanScraper
import com.pointlessapps.filman.data.scraper.NetworkClient
import com.pointlessapps.filman.data.scraper.OpenSubtitlesClient
import com.pointlessapps.filman.data.scraper.TmdbClient
import com.pointlessapps.filman.data.scraper.VideoUrlResolver
import com.pointlessapps.filman.data.scraper.WyzieSubsClient
import com.pointlessapps.filman.data.scraper.extractors.ExtractedVideo
import com.pointlessapps.filman.data.scraper.extractors.Subtitle
import com.pointlessapps.filman.data.scraper.extractors.getExtractorForUrl
import com.pointlessapps.filman.ui.base.BaseEvent
import com.pointlessapps.filman.ui.base.BaseViewModel
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.base.SharedState
import com.pointlessapps.filman.ui.base.StateWithShared
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.OverlayMenuData
import com.pointlessapps.filman.ui.core.TextValue
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.Show
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowInOverlay
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonModel.AppearanceModel.ShowWithTimer
import com.pointlessapps.filman.ui.player.model.NextEpisodeButtonUIState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URL

internal sealed interface PlayerEvent : FilmanEvent {
    data class UpdateSubtitleStyle(val preferences: SubtitleStylePreferences) : PlayerEvent

    data class LoadDetails(
        val url: String,
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

    data class SearchOpenSubtitles(
        val language: String,
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

    data object PlayerError : PlayerEvent

    data class CloudflareCleared(
        val domain: String,
        val cookies: String,
    ) : PlayerEvent
}

@Immutable
internal data class PlayerState(
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
    val selectedSubtitleUrl: String? = null,
    val isWebView: Boolean = false,
    val failedUrls: Set<String> = emptySet(),
    val alternativeSources: List<ExtractedVideo> = emptyList(),
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
    override fun copyWithShared(shared: SharedState) = copy(shared = shared)
}

internal sealed interface PlayerEffect {
    data object NavigateToAuth : PlayerEffect

    data class ShowToast(
        val message: TextValue,
    ) : PlayerEffect
}

internal class PlayerViewModel(
    private val scraper: FilmanScraper,
    private val videoUrlResolver: VideoUrlResolver,
    private val settingsManager: SettingsManager,
    private val tvShowSettingsManager: TvShowSettingsManager,
    progressManager: ProgressManager,
) : BaseViewModel<PlayerState, PlayerEvent, PlayerEffect>(
    initialState = PlayerState(),
    progressManager = progressManager,
) {
    private var preferredSubtitleLanguage: String? = null
    private var preferredSubtitleLabel: String? = null
    private var preferredAudioLanguage: String? = null

    private val openSubtitlesClient by lazy { OpenSubtitlesClient() }
    private val tmdbClient by lazy { TmdbClient(NetworkClient.okHttpClient) }
    private val wyzieSubsClient by lazy { WyzieSubsClient(NetworkClient.okHttpClient, tmdbClient) }

    init {
        launchHandled {
            settingsManager.subtitleStylePreferencesFlow.collect { preferences ->
                updateState { it.copy(subtitleStylePreferences = preferences) }
                if (state.value.shared.overlayMenuData != null) {
                    refreshOverlayMenu()
                }
            }
        }
        val initialModelFlow =
            combine(
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

        val secondaryModelFlow =
            combine(
                settingsManager.secondaryAppearanceTypeFlow,
                settingsManager.secondaryAppearanceOffsetFlow,
                settingsManager.secondaryAppearancePercentageFlow,
                settingsManager.secondaryTimerAmountFlow,
            ) { type, offset, percentage, timerAmount ->
                val percentageOffset = percentage / 100f
                val maxTimeOffset = offset * 1000L
                when (type) {
                    SHOW -> {
                        Show(percentageOffset, maxTimeOffset)
                    }

                    SHOW_IN_OVERLAY -> {
                        ShowInOverlay(percentageOffset, maxTimeOffset)
                    }

                    SHOW_WITH_TIMER -> {
                        ShowWithTimer(
                            percentageOffset,
                            maxTimeOffset,
                            timerAmount * 1000L,
                        )
                    }

                    HIDE -> {
                        null
                    }
                }
            }

        val baseModelFlow =
            combine(initialModelFlow, secondaryModelFlow) { initial, secondary ->
                NextEpisodeButtonModel(initial, secondary)
            }

        val dynamicUiStateFlow =
            combine(
                baseModelFlow,
                state,
            ) { model, currentState ->
                val duration = currentState.duration
                val currentPosition = currentState.currentPositionMs
                val areControlsVisible = currentState.areControlsVisible
                val isInitialDismissed = currentState.isInitialPhaseDismissed
                val isSecondaryDismissed = currentState.isSecondaryPhaseDismissed
                val isTimerCancelled = currentState.isTimerCancelled

                fun isPastThreshold(appearance: NextEpisodeButtonModel.AppearanceModel?): Boolean {
                    if (appearance == null || duration <= 0) return false
                    val threshold =
                        duration -
                                minOf(
                                    appearance.maxTimeOffset,
                                    (duration * appearance.percentageOffset).toLong(),
                                )
                    return currentPosition >= threshold
                }

                val isInitialPhase = isPastThreshold(model.initialAppearanceModel)
                val isSecondaryPhase = isPastThreshold(model.appearanceModel)

                val activeModel =
                    if (isSecondaryPhase) {
                        model.appearanceModel
                    } else if (isInitialPhase) {
                        model.initialAppearanceModel
                    } else {
                        null
                    }

                var isVisible = false
                var shouldRunTimer = false

                val autoPlayNextEpisode = settingsManager.autoPlayNextFlow.value

                when (activeModel) {
                    is Show -> {
                        isVisible = true
                    }

                    is ShowInOverlay -> {
                        isVisible = areControlsVisible
                    }

                    is ShowWithTimer -> {
                        isVisible = true
                        shouldRunTimer =
                            autoPlayNextEpisode && !areControlsVisible && !isTimerCancelled
                    }

                    null -> {
                        isVisible = false
                    }
                }

                if (isSecondaryPhase && isSecondaryDismissed) {
                    isVisible = false
                    shouldRunTimer = false
                } else if (isInitialPhase && !isSecondaryPhase && isInitialDismissed) {
                    isVisible = false
                    shouldRunTimer = false
                }

                if (isVisible && autoPlayNextEpisode) {
                    handleNextEpisodeBoxAppeared()
                }

                NextEpisodeButtonUIState(
                    isVisible = isVisible,
                    isSecondaryPhase = isSecondaryPhase,
                    isTimerRunning = shouldRunTimer,
                    timerDurationMs = (activeModel as? ShowWithTimer)?.timerDuration ?: 0L,
                )
            }

        viewModelScope.launch {
            dynamicUiStateFlow.collect { uiState ->
                updateState { it.copy(nextEpisodeButtonUIState = uiState) }
            }
        }
    }

    override fun getAuthErrorEffect(): PlayerEffect = PlayerEffect.NavigateToAuth

    override fun handleEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.LoadDetails -> {
                loadDetails(event.url)
            }

            is PlayerEvent.IsPlayingChanged -> {
                updateState { it.copy(isPlaying = event.isPlaying) }
            }

            is PlayerEvent.IsBufferingChanged -> {
                updateState { it.copy(isBuffering = event.isBuffering) }
            }

            is PlayerEvent.DurationProvided -> {
                updateState { it.copy(duration = event.duration) }
            }

            is PlayerEvent.ControlsVisibilityChanged -> {
                updateState {
                    val shouldCancelTimer =
                        it.nextEpisodeButtonUIState.isSecondaryPhase && event.isVisible
                    it.copy(
                        areControlsVisible = event.isVisible,
                        isTimerCancelled = shouldCancelTimer || it.isTimerCancelled,
                    )
                }
            }

            is PlayerEvent.CurrentPositionChanged -> {
                updateState {
                    it.copy(currentPositionMs = event.positionMs)
                }
            }

            is PlayerEvent.NextEpisodePromptDismissed -> {
                handleNextEpisodePromptDismissed()
            }

            is PlayerEvent.CancelNextEpisodeTimer -> {
                updateState { it.copy(isTimerCancelled = true) }
            }

            is PlayerEvent.NextEpisodeRequested -> {
                loadNextEpisode()
            }

            is PlayerEvent.SaveProgress -> {
                saveProgress(event.url, event.positionMs)
            }

            is PlayerEvent.OpenSettingsMenu -> {
                openSettingsMenu(
                    currentPositionMs = event.currentPositionMs,
                    initialMenuId = event.initialMenuId,
                )
            }

            is PlayerEvent.ChangeVideoSource -> {
                changeVideoSource(event.source)
            }

            is PlayerEvent.AudioTracksChanged -> {
                val tracks = event.audioTracks
                val preferredTrack =
                    if (state.value.selectedAudioTrackId == null && preferredAudioLanguage != null) {
                        tracks.find {
                            it.language.equals(
                                preferredAudioLanguage,
                                ignoreCase = true,
                            )
                        }
                    } else {
                        null
                    }

                val selectedId =
                    state.value.selectedAudioTrackId?.takeIf { id -> tracks.any { it.id == id } }
                        ?: preferredTrack?.id

                updateState {
                    it.copy(
                        audioTracks = tracks,
                        selectedAudioTrackId = selectedId,
                    )
                }
            }

            is PlayerEvent.SelectAudioTrack -> {
                val selectedTrack = state.value.audioTracks.find { it.id == event.trackId }
                preferredAudioLanguage = selectedTrack?.language
                updateState { it.copy(selectedAudioTrackId = event.trackId) }
            }

            is PlayerEvent.ChangePlaybackSpeed -> {
                updateState { it.copy(playbackSpeed = event.speed) }
                updateTvShowSettings { it.copy(playbackSpeed = event.speed) }
            }

            is PlayerEvent.UpdateSubtitleStyle -> {
                settingsManager.setSubtitleStylePreferences(event.preferences)
            }

            is PlayerEvent.ChangeAspectRatio -> {
                updateState { it.copy(aspectRatioMode = event.mode) }
                updateTvShowSettings { it.copy(aspectRatioMode = event.mode) }
            }

            is PlayerEvent.SelectSubtitle -> {
                val selectedSubtitle =
                    (state.value.subtitles + state.value.openSubtitles).find { it.url == event.subtitleUrl }
                preferredSubtitleLanguage = selectedSubtitle?.language
                preferredSubtitleLabel = selectedSubtitle?.label
                updateState { it.copy(selectedSubtitleUrl = event.subtitleUrl) }
                updateTvShowSettings {
                    it.copy(
                        subtitlesEnabled = event.subtitleUrl != null,
                        subtitleLanguage = selectedSubtitle?.language,
                        subtitleLabel = selectedSubtitle?.label,
                    )
                }
            }

            is PlayerEvent.SearchOpenSubtitles -> {
                searchOpenSubtitles(event.language)
            }

            is PlayerEvent.SearchWyzieSubtitles -> {
                searchWyzieSubtitles(event.language)
            }

            is PlayerEvent.PlayerError -> {
                handlePlayerError()
            }

            is PlayerEvent.CloudflareCleared -> {
                NetworkClient.setCloudflareCookie(event.domain, event.cookies)
            }
        }
    }

    private fun openSettingsMenu(
        currentPositionMs: Long,
        initialMenuId: String? = null,
    ) {
        updateState { it.copy(startPositionMs = currentPositionMs) }
        refreshOverlayMenu(initialMenuId)
    }

    private fun refreshOverlayMenu(initialMenuId: String? = state.value.shared.overlayMenuData?.initialMenuId) {
        val currentUrl = state.value.videoUrl
        val alternatives =
            state.value.detailedMedia?.let {
                videoUrlResolver.getAlternativeUrls(it.baseItem.url)
            } ?: state.value.alternativeSources

        val currentMediaUrl =
            state.value.detailedMedia
                ?.baseItem
                ?.url
        val currentWebsite =
            currentMediaUrl?.let { url ->
                if (url.contains(FilmanConfig.DOMAIN)) {
                    FilmanConfig.DOMAIN
                } else if (url.contains(EkinoConfig.DOMAIN)) {
                    EkinoConfig.DOMAIN
                } else if (url.contains(ZaluknijConfig.DOMAIN)) {
                    ZaluknijConfig.DOMAIN
                } else {
                    ""
                }
            } ?: ""

        val menuItems = mutableListOf<FilmanOverlayMenuItem>()
        val grouped =
            alternatives.groupBy {
                it.sourceWebsite
            }

        val sortedGrouped =
            grouped.toList().sortedBy { (website, _) ->
                when (website) {
                    currentWebsite -> 0
                    FilmanConfig.DOMAIN -> 1
                    else -> 2
                }
            }

        sortedGrouped.forEach { (website, items) ->
            val label =
                if (website.isEmpty()) {
                    TextValue.StringResource(R.string.unknown_source)
                } else {
                    TextValue.DynamicString(
                        website.substringBefore(".").replaceFirstChar { it.titlecase() },
                    )
                }
            menuItems.add(FilmanOverlayMenuItem.Header(label = label))

            items.filterNot { it.url in state.value.failedUrls }.forEach { extracted ->
                val serverName =
                    extracted.serverName.ifEmpty {
                        runCatching { URL(extracted.url).host }.getOrNull().orEmpty()
                    }
                val tags =
                    listOf(serverName, extracted.version, extracted.quality).filter {
                        it.isNotBlank()
                    }

                menuItems.add(
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.DynamicString(tags.joinToString(" • ")),
                        isSelected = extracted.url == currentUrl,
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.ChangeVideoSource(extracted))
                        },
                    ),
                )
            }
        }

        val subtitleItems = mutableListOf<FilmanOverlayMenuItem>()
        val currentSource = alternatives.find { it.url == currentUrl }
        if (currentSource?.subtitles?.isNotEmpty() == true || state.value.openSubtitles.isNotEmpty()) {
            subtitleItems.add(
                FilmanOverlayMenuItem.Option(
                    label = TextValue.StringResource(R.string.player_subtitles_off),
                    isSelected = state.value.selectedSubtitleUrl == null,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SelectSubtitle(null))
                    },
                ),
            )
        }

        currentSource?.subtitles?.forEach { subtitle ->
            subtitleItems.add(
                FilmanOverlayMenuItem.Option(
                    label = TextValue.DynamicString(subtitle.label),
                    isSelected = subtitle.url == state.value.selectedSubtitleUrl,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SelectSubtitle(subtitle.url))
                    },
                ),
            )
        }

        state.value.openSubtitles.forEach { subtitle ->
            subtitleItems.add(
                FilmanOverlayMenuItem.Option(
                    label = TextValue.DynamicString(subtitle.label),
                    isSelected = subtitle.url == state.value.selectedSubtitleUrl,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SelectSubtitle(subtitle.url))
                    },
                ),
            )
        }

        val openSubtitlesLangs =
            listOf("pl", "en", "es", "fr", "de", "it").map { lang ->
                FilmanOverlayMenuItem.Option(
                    label = TextValue.DynamicString(lang.uppercase()),
                    isSelected = false,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SearchOpenSubtitles(lang))
                    },
                )
            }
        subtitleItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_download_open_subtitles),
                value = null,
                items = openSubtitlesLangs,
            ),
        )

        val wyzieSubtitlesLangs =
            listOf("pl", "en", "es", "fr", "de", "it").map { lang ->
                FilmanOverlayMenuItem.Option(
                    label = TextValue.DynamicString(lang.uppercase()),
                    isSelected = false,
                    onClick = {
                        onEvent(BaseEvent.CloseContextMenu)
                        onEvent(PlayerEvent.SearchWyzieSubtitles(lang))
                    },
                )
            }
        subtitleItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_download_wyzie_subs),
                value = null,
                items = wyzieSubtitlesLangs,
            ),
        )

        val overlayItems =
            mutableListOf<FilmanOverlayMenuItem>(
                FilmanOverlayMenuItem.NestedMenu(
                    id = PlayerConstants.MENU_SOURCES_ID,
                    label = TextValue.StringResource(R.string.player_video_source),
                    value = null,
                    items = menuItems,
                ),
            )

        if (state.value.audioTracks.size > 1) {
            val audioItems =
                state.value.audioTracks.map { track ->
                    FilmanOverlayMenuItem.Option(
                        label = TextValue.DynamicString(track.label),
                        isSelected =
                            track.id == state.value.selectedAudioTrackId ||
                                    (state.value.selectedAudioTrackId == null && track.isSelected),
                        onClick = {
                            onEvent(BaseEvent.CloseContextMenu)
                            onEvent(PlayerEvent.SelectAudioTrack(track.id))
                        },
                    )
                }
            overlayItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    label = TextValue.StringResource(R.string.player_audio_track),
                    value = null,
                    items = audioItems,
                ),
            )
        }

        if (subtitleItems.isNotEmpty()) {
            overlayItems.add(
                FilmanOverlayMenuItem.NestedMenu(
                    label = TextValue.StringResource(R.string.player_subtitles),
                    value = null,
                    items = subtitleItems,
                ),
            )
        }

        val stylePrefs = state.value.subtitleStylePreferences
        val fontSizes = listOf(0.03f, 0.0533f, 0.08f, 0.1f, 0.12f, 0.15f)
        val fontSizeItems = fontSizes.map { size ->
            FilmanOverlayMenuItem.Option(
                label = TextValue.DynamicString("${(size * 100).toInt()}%"),
                isSelected = stylePrefs.fontSizeFraction == size,
                onClick = {
                    onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(fontSizeFraction = size)))
                },
            )
        }

        val textColors = listOf(
            Pair(0xFFFFFFFF.toInt(), R.string.color_white),
            Pair(0xFFFFFF00.toInt(), R.string.color_yellow),
        )
        val textColorItems = textColors.map { (color, stringRes) ->
            FilmanOverlayMenuItem.Option(
                label = TextValue.StringResource(stringRes),
                isSelected = stylePrefs.textColorArgb == color,
                onClick = {
                    onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(textColorArgb = color)))
                },
            )
        }

        val backgrounds = listOf(
            Pair(0x00000000.toInt(), R.string.color_transparent),
            Pair(0x80000000.toInt(), R.string.color_black),
        )
        val backgroundItems = backgrounds.map { (color, stringRes) ->
            FilmanOverlayMenuItem.Option(
                label = TextValue.StringResource(stringRes),
                isSelected = stylePrefs.backgroundColorArgb == color,
                onClick = {
                    onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(backgroundColorArgb = color)))
                },
            )
        }

        val edgeTypes = listOf(
            Pair(CaptionStyleCompat.EDGE_TYPE_NONE, R.string.edge_none),
            Pair(CaptionStyleCompat.EDGE_TYPE_OUTLINE, R.string.edge_outline),
            Pair(CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, R.string.edge_shadow),
        )
        val verticalOffsets = listOf(0.01f, 0.05f, 0.1f, 0.15f, 0.2f)
        val verticalOffsetItems = verticalOffsets.map { offset ->
            FilmanOverlayMenuItem.Option(
                label = TextValue.DynamicString("${(offset * 100).toInt()}%"),
                isSelected = stylePrefs.verticalPaddingFraction == offset,
                onClick = {
                    onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(verticalPaddingFraction = offset)))
                },
            )
        }

        val edgeTypeItems = edgeTypes.map { (type, stringRes) ->
            FilmanOverlayMenuItem.Option(
                label = TextValue.StringResource(stringRes),
                isSelected = stylePrefs.edgeType == type,
                onClick = {
                    onEvent(PlayerEvent.UpdateSubtitleStyle(stylePrefs.copy(edgeType = type)))
                },
            )
        }

        val styleItems = listOf(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.subtitle_style_font_size),
                value = "${(stylePrefs.fontSizeFraction * 100).toInt()}%",
                items = fontSizeItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.subtitle_style_text_color),
                value = null,
                items = textColorItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.subtitle_style_background),
                value = null,
                items = backgroundItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.subtitle_style_edge_type),
                value = null,
                items = edgeTypeItems,
            ),
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.subtitle_style_vertical_offset),
                value = null,
                items = verticalOffsetItems,
            ),
        )

        overlayItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.overlay_menu_subtitle_style),
                value = null,
                items = styleItems,
            ),
        )

        overlayItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_playback_speed),
                value = null,
                items =
                    PlayerConstants.PlaybackSpeed.ALL.map { speed ->
                        FilmanOverlayMenuItem.Option(
                            label =
                                TextValue.StringResource(
                                    R.string.player_speed_format,
                                    listOf(speed.toString()),
                                ),
                            isSelected = state.value.playbackSpeed == speed,
                            onClick = {
                                onEvent(BaseEvent.CloseContextMenu)
                                onEvent(PlayerEvent.ChangePlaybackSpeed(speed))
                            },
                        )
                    },
            ),
        )

        overlayItems.add(
            FilmanOverlayMenuItem.NestedMenu(
                label = TextValue.StringResource(R.string.player_aspect_ratio),
                value = null,
                items =
                    listOf(
                        FilmanOverlayMenuItem.Option(
                            label = TextValue.StringResource(R.string.player_aspect_fit),
                            isSelected = state.value.aspectRatioMode == PlayerConstants.AspectRatio.FIT,
                            onClick = {
                                onEvent(BaseEvent.CloseContextMenu)
                                onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.FIT))
                            },
                        ),
                        FilmanOverlayMenuItem.Option(
                            label = TextValue.StringResource(R.string.player_aspect_crop),
                            isSelected = state.value.aspectRatioMode == PlayerConstants.AspectRatio.CROP,
                            onClick = {
                                onEvent(BaseEvent.CloseContextMenu)
                                onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.CROP))
                            },
                        ),
                        FilmanOverlayMenuItem.Option(
                            label = TextValue.StringResource(R.string.player_aspect_stretch),
                            isSelected = state.value.aspectRatioMode == PlayerConstants.AspectRatio.STRETCH,
                            onClick = {
                                onEvent(BaseEvent.CloseContextMenu)
                                onEvent(PlayerEvent.ChangeAspectRatio(PlayerConstants.AspectRatio.STRETCH))
                            },
                        ),
                    ),
            ),
        )

        val menuData =
            OverlayMenuData(
                title = TextValue.StringResource(R.string.player_settings),
                items = overlayItems,
                initialMenuId = initialMenuId,
            )

        updateSharedState { it.copy(overlayMenuData = menuData) }
    }

    private fun getPreferredSubtitleUrl(subtitles: List<Subtitle>): String? {
        if (preferredSubtitleLanguage == null) return null

        return subtitles
            .find {
                it.language == preferredSubtitleLanguage && it.label == preferredSubtitleLabel
            }?.url ?: subtitles.find { it.language == preferredSubtitleLanguage }?.url
    }

    private fun changeVideoSource(source: ExtractedVideo) {
        updateState {
            it.copy(
                videoUrl = source.url,
                videoHeaders = source.headers,
                audioTracks = emptyList(),
                selectedAudioTrackId = null,
                subtitles = source.subtitles,
                selectedSubtitleUrl = getPreferredSubtitleUrl(source.subtitles),
                isWebView = source.isWebView,
            )
        }
        updateTvShowSettings {
            it.copy(
                serverName = source.serverName,
                version = source.version,
                quality = source.quality,
                sourceWebsite = source.sourceWebsite,
            )
        }
    }

    private fun updateTvShowSettings(transform: (TvShowSourceSettings) -> TvShowSourceSettings) {
        val movie = state.value.detailedMedia?.baseItem ?: return
        val showKey = movie.getTvShowKey() ?: return
        val current =
            tvShowSettingsManager.getSettingsForTvShowSync(showKey) ?: TvShowSourceSettings()
        val updated = transform(current)
        tvShowSettingsManager.saveSettingsForTvShow(showKey, updated)
    }

    private fun findMatchingSubtitle(
        subtitles: List<Subtitle>,
        settings: TvShowSourceSettings,
    ): String? {
        if (!settings.subtitlesEnabled || subtitles.isEmpty()) return null

        val lang = settings.subtitleLanguage
        val label = settings.subtitleLabel

        if (!lang.isNullOrBlank() && !label.isNullOrBlank()) {
            val match =
                subtitles.find {
                    it.language.equals(lang, ignoreCase = true) &&
                            it.label.equals(
                                label,
                                ignoreCase = true,
                            )
                }
            if (match != null) return match.url
        }

        if (!lang.isNullOrBlank()) {
            val match = subtitles.find { it.language.equals(lang, ignoreCase = true) }
            if (match != null) return match.url
        }

        if (!label.isNullOrBlank()) {
            val match = subtitles.find { it.label.equals(label, ignoreCase = true) }
            if (match != null) return match.url
        }

        return subtitles.firstOrNull()?.url
    }

    private fun handlePlayerError() {
        val currentUrl = state.value.videoUrl
        val alternatives =
            state.value.detailedMedia?.let {
                videoUrlResolver.getAlternativeUrls(it.baseItem.url)
            } ?: state.value.alternativeSources

        val newFailedUrls = state.value.failedUrls + listOfNotNull(currentUrl)
        updateState { it.copy(failedUrls = newFailedUrls) }

        val currentIndex = alternatives.indexOfFirst { it.url == currentUrl }
        val orderedAlternatives =
            if (currentIndex != -1) {
                alternatives.subList(currentIndex + 1, alternatives.size) + alternatives.subList(
                    0,
                    currentIndex + 1,
                )
            } else {
                alternatives
            }

        val nextSource = orderedAlternatives.firstOrNull { it.url !in newFailedUrls }

        if (nextSource != null) {
            changeVideoSource(nextSource)
        } else {
            updateSharedState {
                it.copy(errorMessage = TextValue.StringResource(R.string.error_all_sources_failed))
            }
        }
    }

    private fun handleNextEpisodePromptDismissed() {
        val isSecondary = state.value.nextEpisodeButtonUIState.isSecondaryPhase
        if (isSecondary) {
            updateState { it.copy(isSecondaryPhaseDismissed = true) }
        } else {
            updateState { it.copy(isInitialPhaseDismissed = true) }
        }
    }

    private fun handleNextEpisodeBoxAppeared() {
        val nextEpisodeUrl =
            state.value.detailedMedia
                ?.baseItem
                ?.nextEpisodeUrl ?: return
        launchHandled {
            videoUrlResolver.prefetch(nextEpisodeUrl)
        }
    }

    private fun saveProgress(
        url: String,
        positionMs: Long,
    ) {
        val detailedMedia = state.value.detailedMedia ?: return
        if (detailedMedia.baseItem.url != url) return
        val duration = state.value.duration
        val item = detailedMedia.baseItem

        progressManager?.saveProgress(item, positionMs, duration)
    }

    private fun loadNextEpisode() {
        val detailedMedia = state.value.detailedMedia ?: return
        val nextEpisodeUrl = detailedMedia.baseItem.nextEpisodeUrl ?: return

        progressManager?.markAsWatched(detailedMedia.baseItem)
        loadDetails(nextEpisodeUrl)
    }

    private fun loadDetails(url: String) {
        val existingProgress = progressManager?.getProgressForUrl(url)
        val startPos = (existingProgress as? ProgressItem.InProgress)?.progressMs ?: 0L

        updateState {
            PlayerState(
                shared =
                    it.shared.copy(
                        isLoading = true,
                        errorMessage = null,
                    ),
                detailedMedia = null,
                videoHeaders = emptyMap(),
                videoUrl = null,
                subtitles = emptyList(),
                selectedSubtitleUrl = null,
                startPositionMs = startPos,
                isWebView = false,
                failedUrls = emptySet(),
                alternativeSources = emptyList(),
            )
        }

        launchHandled {
            val isDirectYoutube =
                url.contains("youtube.com", ignoreCase = true) ||
                        url.contains("youtu.be", ignoreCase = true)

            if (isDirectYoutube) {
                updateState {
                    it.copy(
                        shared = it.shared.copy(isLoading = false),
                        detailedMedia = null,
                    )
                }

                val extractor = getExtractorForUrl(url)
                val extractedList = extractor?.extractVideo(url) ?: emptyList()

                if (extractedList.isNotEmpty()) {
                    val preferredQuality = settingsManager.preferredQualityFlow.first()
                    val bestExtracted =
                        if (preferredQuality == SettingsConstants.Quality.AUTO) {
                            extractedList.first()
                        } else {
                            extractedList.find {
                                it.quality.contains(preferredQuality, ignoreCase = true) ||
                                        it.version.contains(preferredQuality, ignoreCase = true)
                            } ?: extractedList.first()
                        }

                    updateState {
                        it.copy(
                            videoHeaders = bestExtracted.headers,
                            videoUrl = bestExtracted.url,
                            subtitles = bestExtracted.subtitles,
                            selectedSubtitleUrl = getPreferredSubtitleUrl(bestExtracted.subtitles),
                            startPositionMs = 0L,
                            isWebView = bestExtracted.isWebView,
                            alternativeSources = extractedList,
                        )
                    }
                } else {
                    updateSharedState {
                        it.copy(
                            isLoading = false,
                            errorMessage = TextValue.StringResource(R.string.error_no_playable_video),
                        )
                    }
                }
                return@launchHandled
            }

            var detailedMedia = scraper.getMediaDetails(url)
            if (detailedMedia == null) {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(com.pointlessapps.filman.R.string.error_fallback_loading),
                    ),
                )
                val fallbackUrl = scraper.resolveFallbackUrl(url)
                if (fallbackUrl != null) {
                    detailedMedia = scraper.getMediaDetails(fallbackUrl)
                }
            }
            val details = detailedMedia?.baseItem
            if (details == null) {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.error_media_not_found),
                    )
                }
                return@launchHandled
            }

            val tvShowKey = details.getTvShowKey()
            val savedSettings =
                if (tvShowKey != null) {
                    tvShowSettingsManager.getSettingsForTvShow(tvShowKey)
                } else {
                    null
                }

            videoUrlResolver.prefetch(url, detailedMedia)
            var extracted = videoUrlResolver.getFastest(url, targetSettings = savedSettings)

            if (extracted == null) {
                scraper.invalidateMediaCache(url)
                detailedMedia = scraper.getMediaDetails(url)
                if (detailedMedia != null) {
                    videoUrlResolver.prefetch(url, detailedMedia)
                    extracted = videoUrlResolver.getFastest(url, targetSettings = savedSettings)
                }
            }

            if (extracted == null) {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(com.pointlessapps.filman.R.string.error_fallback_no_video),
                    ),
                )
                val fallbackUrl = scraper.resolveFallbackUrl(url)
                if (fallbackUrl != null) {
                    val fallbackMedia = scraper.getMediaDetails(fallbackUrl)
                    if (fallbackMedia != null) {
                        videoUrlResolver.prefetch(fallbackUrl, fallbackMedia)
                        extracted =
                            videoUrlResolver.getFastest(fallbackUrl, targetSettings = savedSettings)
                        if (extracted != null) {
                            detailedMedia = fallbackMedia
                        }
                    }
                }
            }

            if (extracted != null) {
                val selectedSubtitleUrl =
                    if (savedSettings != null) {
                        if (savedSettings.subtitlesEnabled) {
                            preferredSubtitleLanguage = savedSettings.subtitleLanguage
                            preferredSubtitleLabel = savedSettings.subtitleLabel
                            findMatchingSubtitle(extracted.subtitles, savedSettings)
                        } else {
                            preferredSubtitleLanguage = null
                            preferredSubtitleLabel = null
                            null
                        }
                    } else {
                        getPreferredSubtitleUrl(extracted.subtitles)
                    }

                val speed = savedSettings?.playbackSpeed ?: state.value.playbackSpeed
                val aspect = savedSettings?.aspectRatioMode ?: state.value.aspectRatioMode

                updateState {
                    it.copy(
                        shared = it.shared.copy(isLoading = false),
                        detailedMedia = detailedMedia,
                        videoHeaders = extracted.headers,
                        videoUrl = extracted.url,
                        subtitles = extracted.subtitles,
                        selectedSubtitleUrl = selectedSubtitleUrl,
                        startPositionMs = startPos,
                        playbackSpeed = speed,
                        aspectRatioMode = aspect,
                        isWebView = extracted.isWebView,
                    )
                }

                if (tvShowKey != null && savedSettings == null) {
                    val initialSettings =
                        TvShowSourceSettings(
                            serverName = extracted.serverName,
                            version = extracted.version,
                            quality = extracted.quality,
                            sourceWebsite = extracted.sourceWebsite,
                            subtitlesEnabled = selectedSubtitleUrl != null,
                            subtitleLanguage = extracted.subtitles.find { it.url == selectedSubtitleUrl }?.language,
                            subtitleLabel = extracted.subtitles.find { it.url == selectedSubtitleUrl }?.label,
                            playbackSpeed = speed,
                            aspectRatioMode = aspect,
                        )
                    tvShowSettingsManager.saveSettingsForTvShow(tvShowKey, initialSettings)
                }

                saveProgress(detailedMedia!!.baseItem.url, startPos)
            } else {
                updateSharedState {
                    it.copy(
                        isLoading = false,
                        errorMessage = TextValue.StringResource(R.string.error_no_playable_video),
                    )
                }
            }
        }
    }

    private fun searchOpenSubtitles(language: String) {
        val detailedMedia = state.value.detailedMedia ?: return
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl

        launchHandled {
            val link = openSubtitlesClient.searchAndDownload(title, language)
            if (link != null) {
                val srtLink = if (link.contains("?")) "$link&ext=.srt" else "$link?ext=.srt"
                val newSubtitle =
                    Subtitle(
                        url = srtLink,
                        label = "OpenSubtitles ($language)",
                        language = language,
                    )
                val updatedOpenSubtitles = state.value.openSubtitles + newSubtitle
                updateState {
                    it.copy(
                        openSubtitles = updatedOpenSubtitles,
                        selectedSubtitleUrl = srtLink,
                    )
                }
            } else {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(
                            R.string.error_no_open_subtitles_found,
                            language,
                        ),
                    ),
                )
            }
        }
    }

    private fun searchWyzieSubtitles(language: String) {
        val detailedMedia = state.value.detailedMedia ?: return
        val title = detailedMedia.baseItem.titleEn ?: detailedMedia.baseItem.titlePl
        val year = detailedMedia.baseItem.year
        val season = detailedMedia.baseItem.seasonNumber
        val episode = detailedMedia.baseItem.episodeNumber

        launchHandled {
            val subs = wyzieSubsClient.searchSubtitles(title, year, season, episode)
            val filteredSubs = subs.filter { it.language.equals(language, ignoreCase = true) }

            if (filteredSubs.isNotEmpty()) {
                val newSubtitles =
                    filteredSubs.mapIndexed { index, sub ->
                        val srtLink =
                            if (sub.url.contains("?")) "${sub.url}&ext=.srt" else "${sub.url}?ext=.srt"
                        Subtitle(
                            url = srtLink,
                            label = "Wyzie (${sub.display}) ${if (index > 0) "#${index + 1}" else ""}".trim(),
                            language = sub.language,
                        )
                    }
                val updatedOpenSubtitles = state.value.openSubtitles + newSubtitles
                updateState {
                    it.copy(
                        openSubtitles = updatedOpenSubtitles,
                        selectedSubtitleUrl = newSubtitles.first().url,
                    )
                }
            } else {
                sendEffect(
                    PlayerEffect.ShowToast(
                        TextValue.StringResource(
                            R.string.error_no_wyzie_subtitles_found,
                            language,
                        ),
                    ),
                )
            }
        }
    }
}
