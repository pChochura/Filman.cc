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
    sealed interface Home : Route {
        @Serializable
        @Parcelize
        data object Home : Route.Home

        @Serializable
        @Parcelize
        data object Movies : Route.Home

        @Serializable
        @Parcelize
        data object TvShows : Route.Home

        @Serializable
        @Parcelize
        data object ForKids : Route.Home
    }

    @Serializable
    @Parcelize
    data class Details(val url: String) : Route

    @Serializable
    @Parcelize
    data class Player(val url: String) : Route
}
