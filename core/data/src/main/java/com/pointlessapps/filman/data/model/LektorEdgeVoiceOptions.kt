package com.pointlessapps.filman.data.model

object LektorEdgeVoiceOptions {
    const val DEFAULT_RATE = "+10%"
    const val DEFAULT_PITCH = "+0Hz"
    const val DEFAULT_VOLUME = "+0%"
    const val DEFAULT_CALM_MODE = true

    private const val DEFAULT_RATE_PERCENT = 10
    private const val MIN_RATE_PERCENT = -30
    private const val MAX_RATE_PERCENT = 45

    val rates = listOf("-20%", "-10%", "+0%", "+5%", "+10%", "+15%", "+25%", "+35%")
    val pitches = listOf("-20Hz", "-10Hz", "-6Hz", "-3Hz", "+0Hz", "+3Hz", "+6Hz", "+10Hz", "+20Hz")
    val volumes = listOf("-50%", "-35%", "-25%", "-15%", "+0%", "+15%", "+30%")

    fun fromStoredRate(stored: String?): String =
        stored?.takeIf { it in rates } ?: DEFAULT_RATE

    fun fromStoredPitch(stored: String?): String =
        stored?.takeIf { it in pitches } ?: DEFAULT_PITCH

    fun fromStoredVolume(stored: String?): String =
        stored?.takeIf { it in volumes } ?: DEFAULT_VOLUME

    fun parseRatePercent(rate: String): Int =
        rate.trim().removeSuffix("%").toIntOrNull() ?: DEFAULT_RATE_PERCENT

    fun formatRatePercent(percent: Int): String {
        val clamped = percent.coerceIn(MIN_RATE_PERCENT, MAX_RATE_PERCENT)
        return if (clamped >= 0) "+$clamped%" else "$clamped%"
    }
}
