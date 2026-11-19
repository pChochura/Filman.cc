package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.LektorDucking
import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.data.model.LektorTiming
import com.pointlessapps.filman.player.lektor.engine.LektorModelManager
import com.pointlessapps.filman.player.lektor.engine.LektorVoiceTester
import com.pointlessapps.filman.player.lektor.engine.ModelDownloadState
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceCatalog
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LektorUiState(
    val isEnabled: Boolean = false,
    val hideSubtitles: Boolean = false,
    val voiceId: String = "edge_marek",
    val duckingVolume: Float = LektorDucking.DEFAULT_VOLUME,
    val timingOffsetMs: Int = LektorTiming.DEFAULT_OFFSET_MS,
    val edgeRate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
    val edgePitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
    val edgeVolume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    val edgeCalmMode: Boolean = LektorEdgeVoiceOptions.DEFAULT_CALM_MODE,
    val downloadedVoices: Set<String> = emptySet(),
    val modelDownloadProgress: Float? = null,
    val isModelExtracting: Boolean = false,
    val systemVoices: SystemVoiceList = SystemVoiceList(),
)

class LektorControllerDelegate(
    scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val lektorModelManager: LektorModelManager,
    private val systemVoiceCatalog: SystemVoiceCatalog? = null,
    private val voiceTester: LektorVoiceTester? = null,
    private val onDownloadError: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow(
        LektorUiState(
            isEnabled = settingsManager.isLektorEnabled.valueOrDefault(false),
            hideSubtitles = settingsManager.lektorHideSubtitles.valueOrDefault(false),
            voiceId = settingsManager.lektorVoiceId.valueOrDefault("edge_marek"),
            duckingVolume = settingsManager.lektorDuckingVolume.valueOrDefault(LektorDucking.DEFAULT_VOLUME),
            timingOffsetMs = settingsManager.lektorTimingOffsetMs.valueOrDefault(LektorTiming.DEFAULT_OFFSET_MS),
            edgeRate = settingsManager.lektorEdgeRate.valueOrDefault(LektorEdgeVoiceOptions.DEFAULT_RATE),
            edgePitch = settingsManager.lektorEdgePitch.valueOrDefault(LektorEdgeVoiceOptions.DEFAULT_PITCH),
            edgeVolume = settingsManager.lektorEdgeVolume.valueOrDefault(LektorEdgeVoiceOptions.DEFAULT_VOLUME),
            edgeCalmMode = settingsManager.lektorEdgeCalmMode.valueOrDefault(LektorEdgeVoiceOptions.DEFAULT_CALM_MODE),
            downloadedVoices = lektorModelManager.getDownloadedVoices(),
        ),
    )
    val state: StateFlow<LektorUiState> = _state.asStateFlow()

    init {
        systemVoiceCatalog?.let { catalog ->
            catalog.refresh()
            scope.launch {
                catalog.voices.collect { voices ->
                    _state.update { it.copy(systemVoices = voices) }
                }
            }
        }

        scope.launch {
            lektorModelManager.downloadedVoices.collect { voices ->
                _state.update { it.copy(downloadedVoices = voices) }
            }
        }

        scope.launch {
            lektorModelManager.downloadState.collect { downloadState ->
                when (downloadState) {
                    is ModelDownloadState.Downloading -> {
                        _state.update {
                            it.copy(modelDownloadProgress = downloadState.progress, isModelExtracting = false)
                        }
                    }
                    ModelDownloadState.Extracting -> {
                        _state.update {
                            it.copy(modelDownloadProgress = null, isModelExtracting = true)
                        }
                    }
                    ModelDownloadState.Ready,
                    ModelDownloadState.Idle,
                    -> {
                        _state.update {
                            it.copy(
                                modelDownloadProgress = null,
                                isModelExtracting = false,
                                downloadedVoices = lektorModelManager.getDownloadedVoices(),
                            )
                        }
                    }
                    is ModelDownloadState.Error -> {
                        _state.update {
                            it.copy(modelDownloadProgress = null, isModelExtracting = false)
                        }
                        onDownloadError(downloadState.message)
                    }
                }
            }
        }

        scope.launch {
            combine(
                settingsManager.isLektorEnabled,
                settingsManager.lektorVoiceId,
                settingsManager.lektorDuckingVolume,
                settingsManager.lektorTimingOffsetMs,
                settingsManager.lektorHideSubtitles,
            ) { enabled, voiceId, ducking, offset, hideSubtitles ->
                { current: LektorUiState ->
                    current.copy(
                        isEnabled = enabled,
                        voiceId = voiceId,
                        duckingVolume = ducking,
                        timingOffsetMs = offset,
                        hideSubtitles = hideSubtitles,
                    )
                }
            }.collect { transform ->
                _state.update(transform)
            }
        }

        scope.launch {
            combine(
                settingsManager.lektorEdgeRate,
                settingsManager.lektorEdgePitch,
                settingsManager.lektorEdgeVolume,
                settingsManager.lektorEdgeCalmMode,
            ) { rate, pitch, volume, calmMode ->
                EdgeOptionsSnapshot(rate, pitch, volume, calmMode)
            }.collect { (rate, pitch, volume, calmMode) ->
                _state.update {
                    it.copy(edgeRate = rate, edgePitch = pitch, edgeVolume = volume, edgeCalmMode = calmMode)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        settingsManager.setLektorEnabled(enabled)
        _state.update { it.copy(isEnabled = enabled) }
        if (enabled) {
            val selectedVoice = LektorVoice.fromId(_state.value.voiceId)
            if (!lektorModelManager.isModelReady(selectedVoice)) {
                lektorModelManager.startDownload(selectedVoice)
            }
        }
    }

    fun selectVoice(voiceId: String) {
        settingsManager.setLektorVoiceId(voiceId)
        _state.update { it.copy(voiceId = voiceId) }
    }

    fun setHideSubtitles(hide: Boolean) {
        settingsManager.setLektorHideSubtitles(hide)
        _state.update { it.copy(hideSubtitles = hide) }
    }

    fun setDuckingVolume(volume: Float) {
        settingsManager.setLektorDuckingVolume(volume)
        _state.update { it.copy(duckingVolume = volume) }
    }

    fun setTimingOffsetMs(offsetMs: Int) {
        settingsManager.setLektorTimingOffsetMs(offsetMs)
        _state.update { it.copy(timingOffsetMs = offsetMs) }
    }

    fun setEdgeRate(rate: String) {
        settingsManager.setLektorEdgeRate(rate)
        _state.update { it.copy(edgeRate = rate) }
    }

    fun setEdgePitch(pitch: String) {
        settingsManager.setLektorEdgePitch(pitch)
        _state.update { it.copy(edgePitch = pitch) }
    }

    fun setEdgeVolume(volume: String) {
        settingsManager.setLektorEdgeVolume(volume)
        _state.update { it.copy(edgeVolume = volume) }
    }

    fun setEdgeCalmMode(enabled: Boolean) {
        settingsManager.setLektorEdgeCalmMode(enabled)
        _state.update { it.copy(edgeCalmMode = enabled) }
    }

    fun downloadModel(voiceId: String) {
        lektorModelManager.startDownload(LektorVoice.fromId(voiceId))
        _state.update { it.copy(downloadedVoices = lektorModelManager.getDownloadedVoices()) }
    }

    fun deleteModel(voiceId: String) {
        lektorModelManager.deleteModel(LektorVoice.fromId(voiceId))
        _state.update { it.copy(downloadedVoices = lektorModelManager.getDownloadedVoices()) }
    }

    fun previewSystemVoice(name: String?) {
        systemVoiceCatalog?.preview(name)
    }

    fun refreshSystemVoices() {
        systemVoiceCatalog?.refresh()
    }

    fun testVoice() {
        val current = _state.value
        if (voiceTester != null) {
            voiceTester.testVoice(
                voiceId = current.voiceId,
                edgeRate = current.edgeRate,
                edgePitch = current.edgePitch,
                edgeVolume = current.edgeVolume,
            )
        } else {
            val selectedVoice = LektorVoice.fromId(current.voiceId)
            if (selectedVoice.isSystem) {
                systemVoiceCatalog?.preview(LektorVoice.systemVoiceName(current.voiceId))
            }
        }
    }
}

private data class EdgeOptionsSnapshot(
    val rate: String,
    val pitch: String,
    val volume: String,
    val calmMode: Boolean,
)

private inline fun <reified T> StateFlow<T>.valueOrDefault(default: T): T =
    (this as StateFlow<Any?>).value as? T ?: default
