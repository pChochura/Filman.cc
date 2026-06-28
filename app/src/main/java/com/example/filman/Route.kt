package com.example.filman

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed interface Route {
    @Serializable
    @Immutable
    data object Auth : Route

    @Serializable
    @Immutable
    data object Home : Route

    @Serializable
    @Immutable
    data class Details(val url: String) : Route

    @Serializable
    @Immutable
    data class Player(val url: String) : Route
}
