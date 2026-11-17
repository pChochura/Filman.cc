package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class SubtitleCandidate(
    val id: String,
    val name: String,
    val language: String,
    val release: String? = null,
    val provider: String,
    val downloadCount: Int? = null,
    val rating: Double? = null,
    val foreignPartsOnly: Boolean = false,
    val hearingImpaired: Boolean = false,
    val openSubtitleFileId: Int? = null,
    val directUrl: String? = null,
    val format: String = "srt",
)
