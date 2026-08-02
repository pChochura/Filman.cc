package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class Rating(
    val score: Float,
    val maxValue: Float,
) {
    val normalizedScore: Float
        get() = if (maxValue > 0f) {
            score * (MAX_SCORE / maxValue)
        } else {
            score
        }

    private companion object {
        const val MAX_SCORE = 10f
    }
}
