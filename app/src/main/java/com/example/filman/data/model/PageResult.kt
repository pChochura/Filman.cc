package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class PageResult(
    val featuredItems: List<MovieItem>,
    val movies: List<MovieItem>,
    val errorMessage: String? = null,
    val path: String = "",
)
