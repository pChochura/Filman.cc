package com.pointlessapps.filman.data.model

object LektorDucking {
    const val DEFAULT_VOLUME = 1.0f
    val levels = listOf(1.0f, 0.8f, 0.7f, 0.6f)

    fun fromStored(volume: Float): Float = when (volume) {
        0.12f -> 0.6f
        0.22f -> 0.7f
        0.35f, 0.75f -> 0.8f
        else -> levels.minBy { kotlin.math.abs(it - volume) }
    }
}
