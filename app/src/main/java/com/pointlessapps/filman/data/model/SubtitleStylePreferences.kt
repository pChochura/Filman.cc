package com.pointlessapps.filman.data.model

import androidx.media3.ui.CaptionStyleCompat
import kotlinx.serialization.Serializable

@Serializable
data class SubtitleStylePreferences(
    val fontSizeDp: Float = 16f,
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundColorArgb: Int = 0x00000000.toInt(),
    val edgeType: Int = CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
    val edgeColorArgb: Int = 0xFF000000.toInt(),
    val verticalPaddingFraction: Float = 0.05f
)
