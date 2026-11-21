package com.pointlessapps.filman.data.sync.model

import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MediaType
import kotlinx.serialization.Serializable

@Serializable
data class SyncAuthRequest(
    val username: String,
    val password: String,
)

@Serializable
data class SyncAuthResponse(
    val token: String? = null,
    val user: SyncUser? = null,
    val success: Boolean = true,
    val message: String? = null,
)

@Serializable
data class SyncUser(
    val id: String = "",
    val username: String = "",
)

@Serializable
data class SyncProgressEntry(
    val url: String = "",
    val parentUrl: String? = null,
    val type: MediaType = MediaType.MOVIE,
    val tmdbId: Int = 0,
    val season: Int? = null,
    val episode: Int? = null,
    val hasNextEpisode: Boolean = false,
    val progressPercentage: Float = 0f,
    val progressMs: Long = 0L,
    val posterUrl: String = "",
    val titlePl: String = "",
    val seriesTitle: String? = null,
    val episodeTitle: String? = null,
    val isWatched: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun resolveMediaRef(): MediaRef? =
        if (tmdbId > 0) {
            MediaRef(type = type, tmdbId = tmdbId, season = season, episode = episode)
        } else if (url.isNotBlank()) {
            MediaRef.parse(url, season != null || parentUrl?.startsWith("show_") == true)
        } else {
            null
        }
}

@Serializable
data class SyncMediaEntry(
    val url: String = "",
    val type: MediaType = MediaType.MOVIE,
    val tmdbId: Int = 0,
    val titlePl: String = "",
    val posterUrl: String = "",
    val backgroundUrl: String? = null,
    val isTvShow: Boolean = false,
    val year: Int? = null,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun resolveMediaRef(): MediaRef? =
        if (tmdbId > 0) {
            MediaRef(type = type, tmdbId = tmdbId)
        } else if (url.isNotBlank()) {
            MediaRef.parse(url, isTvShow)?.parentRef
        } else {
            null
        }
}

@Serializable
data class SyncPullResponse(
    val serverTimestamp: Long = 0L,
    val progress: List<SyncProgressEntry> = emptyList(),
    val watchlist: List<SyncMediaEntry> = emptyList(),
    val favorites: List<SyncMediaEntry> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
)

@Serializable
data class SyncPushRequest(
    val clientTimestamp: Long = System.currentTimeMillis(),
    val progress: List<SyncProgressEntry> = emptyList(),
    val watchlist: List<SyncMediaEntry> = emptyList(),
    val favorites: List<SyncMediaEntry> = emptyList(),
    val preferences: Map<String, String> = emptyMap(),
)

@Serializable
data class SyncPushResponse(
    val serverTimestamp: Long = 0L,
    val success: Boolean = true,
    val message: String? = null,
)
