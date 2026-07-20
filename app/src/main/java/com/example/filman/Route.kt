package com.example.filman

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : Parcelable {
    val showNavigationBar: Boolean
        get() = true

    @Serializable
    @Parcelize
    data object Auth : Route {
        override val showNavigationBar: Boolean
            get() = false
    }

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
    data class Details(
        val url: String,
        val episodeUrl: String? = null,
    ) : Route {
        override val showNavigationBar: Boolean
            get() = false
    }

    @Serializable
    @Parcelize
    data class Actor(val url: String) : Route {
        override val showNavigationBar: Boolean
            get() = false
    }

    @Serializable
    @Parcelize
    data class Player(val url: String) : Route {
        override val showNavigationBar: Boolean
            get() = false
    }
}
