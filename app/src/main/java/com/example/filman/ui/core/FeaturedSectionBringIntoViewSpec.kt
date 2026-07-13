package com.example.filman.ui.core

import androidx.compose.foundation.gestures.BringIntoViewSpec

internal class FeaturedSectionBringIntoViewSpec(
    private val parentFraction: Float = 0.0f,
    private val childFraction: Float = 0.0f,
) : BringIntoViewSpec {

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val targetLeadingEdge = parentFraction * containerSize - (childFraction * size)

        return offset - targetLeadingEdge
    }
}
