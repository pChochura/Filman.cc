package com.example.filman

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : Parcelable {
    @Serializable
    @Parcelize
    data object Auth : Route

    @Serializable
    @Parcelize
    data object Search : Route

    @Serializable
    @Parcelize
    data object Home : Route

    @Serializable
    @Parcelize
    data object Movies : Route

    @Serializable
    @Parcelize
    data object TvShows : Route

    @Serializable
    @Parcelize
    data object ForKids : Route

    @Serializable
    @Parcelize
    data class Details(val url: String) : Route

    @Serializable
    @Parcelize
    data class Player(val url: String) : Route
}
