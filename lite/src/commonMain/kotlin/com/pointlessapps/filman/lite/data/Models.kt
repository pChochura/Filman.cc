package com.pointlessapps.filman.lite.data

import kotlinx.serialization.Serializable

data class MediaItem(
    val id: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val isTvShow: Boolean,
)

data class Episode(val season: Int, val episode: Int, val name: String) {
    val label: String get() = "S$season E$episode" + if (name.isNotBlank()) " · $name" else ""
}

data class Season(val number: Int, val name: String, val episodes: List<Episode>)

@Serializable
data class StreamSource(
    val url: String,
    val quality: String = "",
    val language: String = "",
    val provider: String = "",
    val headers: Map<String, String> = emptyMap(),
) {
    val label: String
        get() = listOf(provider, language, quality).filter { it.isNotBlank() }.joinToString(" · ")
            .ifBlank { "Source" }
}

open class LiteException(message: String) : Exception(message)

/** The Filman session is missing or expired, so the user has to log in. */
class AuthRequiredException(message: String) : LiteException(message)
