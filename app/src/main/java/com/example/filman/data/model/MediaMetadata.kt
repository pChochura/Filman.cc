package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
@Immutable
data class MediaMetadata(
    val year: Int?,
    val views: Int?,
    val duration: Duration?,
    val countries: List<String>,
)
