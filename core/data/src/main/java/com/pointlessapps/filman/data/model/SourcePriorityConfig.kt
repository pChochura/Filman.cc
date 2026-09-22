package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class SourcePriorityConfig(
    val name: String,
    val isEnabled: Boolean = true,
    val extractors: List<ExtractorPriorityConfig> = emptyList()
)

@Serializable
@Immutable
data class ExtractorPriorityConfig(
    val name: String,
    val isEnabled: Boolean = true
)
