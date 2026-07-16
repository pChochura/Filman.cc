package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class SimilarMovie(
    val url: String,
    val name: String,
    val posterUrl: String,
)
