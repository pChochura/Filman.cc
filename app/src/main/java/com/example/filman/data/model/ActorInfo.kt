package com.example.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class ActorInfo(
    val role: ActorRole,
    val name: String,
    val avatarUrl: String?,
    val url: String?,
)

enum class ActorRole {
    DIRECTOR,
    WRITER,
    ACTOR,
    UNKNOWN
}
