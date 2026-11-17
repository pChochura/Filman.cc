package com.pointlessapps.filman.data.model

object LektorEdgeVoiceOptions {
    const val DEFAULT_RATE = "+15%"
    const val DEFAULT_PITCH = "+0Hz"
    const val DEFAULT_VOLUME = "+0%"

    val rates = listOf("-20%", "-10%", "+0%", "+15%", "+25%", "+35%")
    val pitches = listOf("-20Hz", "-10Hz", "+0Hz", "+10Hz", "+20Hz")
    val volumes = listOf("-15%", "+0%", "+15%", "+30%")

    fun fromStoredRate(stored: String?): String {
        return if (stored in rates) stored!! else DEFAULT_RATE
    }

    fun fromStoredPitch(stored: String?): String {
        return if (stored in pitches) stored!! else DEFAULT_PITCH
    }

    fun fromStoredVolume(stored: String?): String {
        return if (stored in volumes) stored!! else DEFAULT_VOLUME
    }
}
