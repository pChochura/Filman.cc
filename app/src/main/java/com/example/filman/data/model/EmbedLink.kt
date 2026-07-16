package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class EmbedLink(
    val url: String,
    val serverName: String,
    val version: String = "",
    val quality: String = "",
)
