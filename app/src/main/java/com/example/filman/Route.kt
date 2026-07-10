package com.example.filman

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Immutable
sealed interface Route : Parcelable {
    @Serializable
    @Immutable
    @Parcelize
    data object Auth : Route

    @Serializable
    @Immutable
    @Parcelize
    data object Home : Route

    @Serializable
    @Immutable
    @Parcelize
    data class Details(val url: String) : Route

    @Serializable
    @Immutable
    @Parcelize
    data class Player(val url: String) : Route
}
