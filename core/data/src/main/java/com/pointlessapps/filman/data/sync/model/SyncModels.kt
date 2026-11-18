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
    val type: MediaType,
    val tmdbId: Int,
    val season: Int? = null,
    val episode: Int? = null,
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
        } else {
            null
        }
}

@Serializable
data class SyncMediaEntry(
    val type: MediaType,
    val tmdbId: Int,
    val titlePl: String,
    val posterUrl: String = "",
    val backgroundUrl: String? = null,
    val year: Int? = null,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun resolveMediaRef(): MediaRef? =
        if (tmdbId > 0) {
            MediaRef(type = type, tmdbId = tmdbId)
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
