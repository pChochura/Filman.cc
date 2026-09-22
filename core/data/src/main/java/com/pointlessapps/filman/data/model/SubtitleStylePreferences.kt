package com.pointlessapps.filman.data.model


import kotlinx.serialization.Serializable

@Serializable
data class SubtitleStylePreferences(
    val fontSizeFraction: Float = 0.0533f,
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundColorArgb: Int = 0x00000000.toInt(),
    val edgeType: Int = 2,
    val edgeColorArgb: Int = 0xFF000000.toInt(),
    val verticalPaddingFraction: Float = 0.05f
)
