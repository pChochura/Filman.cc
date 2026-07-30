package com.example.filman

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : Parcelable {
    val showNavigationBar: Boolean
        get() = true

    val showBackButton: Boolean
        get() = false

    @Serializable
    @Parcelize
    data class Login(
        val returnRoute: Route? = null,
        val replaceCurrentRoute: Boolean = true,
    ) : Route {
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
        val autoPlay: Boolean = false,
        val episodeUrl: String? = null,
    ) : Route {
        override val showNavigationBar: Boolean
            get() = false

        override val showBackButton: Boolean
            get() = true
    }

    @Serializable
    @Parcelize
    data class Actor(val url: String) : Route {
        override val showNavigationBar: Boolean
            get() = false

        override val showBackButton: Boolean
            get() = true
    }

    @Serializable
    @Parcelize
    data class Player(val url: String) : Route {
        override val showNavigationBar: Boolean
            get() = false
    }
}
