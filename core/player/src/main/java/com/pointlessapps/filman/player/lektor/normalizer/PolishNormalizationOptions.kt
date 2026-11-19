package com.pointlessapps.filman.player.lektor.normalizer

enum class PolishNormalizationMode {
    NEURAL,
    OFFLINE,
}

data class PolishNormalizationOptions(
    val mode: PolishNormalizationMode = PolishNormalizationMode.OFFLINE,
    val calmExclamations: Boolean = false,
)
