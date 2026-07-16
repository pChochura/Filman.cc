package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class CategoryInfo(
    val name: String,
    val url: String,
    val id: Int,
)
