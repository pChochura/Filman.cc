package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class ActorDetails(
    val name: String,
    val avatarUrl: String,
    val birthDate: String?,
    val birthPlace: String?,
    val height: String?,
    val description: String?,
    val filmwebRating: Rating?,
    val moviesDirector: List<MovieItem>,
    val moviesWriter: List<MovieItem>,
    val moviesCast: List<MovieItem>,
)
