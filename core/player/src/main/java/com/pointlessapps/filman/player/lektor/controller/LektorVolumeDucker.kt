package com.pointlessapps.filman.player.lektor.controller

import com.pointlessapps.filman.data.model.LektorDucking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class LektorVolumeDucker(
    private val scope: CoroutineScope,
    private val onDuckingVolumeChange: (Float) -> Unit,
) {
    private val _duckingVolume = MutableStateFlow(LektorDucking.DEFAULT_VOLUME)
    val duckingVolume: StateFlow<Float> = _duckingVolume.asStateFlow()

    private var activeDuckingJob: Job? = null
    private var isDucking = false
    private var appliedVolume = 1.0f

    fun setDuckingVolume(volume: Float) {
        require(volume in LektorDucking.levels)
        _duckingVolume.value = volume
        if (isDucking) duck()
    }

    fun duck() {
        activeDuckingJob?.cancel()
        activeDuckingJob = scope.launch(Dispatchers.Main.immediate) {
            isDucking = true
            changeVolume(_duckingVolume.value)
        }
    }

    fun restore() {
        activeDuckingJob?.cancel()
        activeDuckingJob = scope.launch(Dispatchers.Main.immediate) {
            isDucking = false
            changeVolume(1.0f)
        }
    }

    fun releaseImmediate() {
        activeDuckingJob?.cancel()
        isDucking = false
        if (appliedVolume != 1.0f) {
            appliedVolume = 1.0f
            onDuckingVolumeChange(1.0f)
        }
    }

    private suspend fun changeVolume(target: Float) {
        if (appliedVolume == target) return
        appliedVolume = (appliedVolume + target) / 2
        onDuckingVolumeChange(appliedVolume)
        delay(30)
        appliedVolume = target
        onDuckingVolumeChange(target)
    }
}
