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
        val path: String

        @Serializable
        @Parcelize
        data object Search : Route.Home {
            override val path: String
                get() = "/search"
        }

        @Serializable
        @Parcelize
        data object Home : Route.Home {
            override val path: String
                get() = "/"
        }

        @Serializable
        @Parcelize
        data object Movies : Route.Home {
            override val path: String
                get() = "/filmy/"
        }

        @Serializable
        @Parcelize
        data object TvShows : Route.Home {
            override val path: String
                get() = "/seriale/"
        }

        @Serializable
        @Parcelize
        data object ForKids : Route.Home {
            override val path: String
                get() = "/dla-dzieci-pl/"
        }
    }

    @Serializable
    @Parcelize
    data class Details(val url: String) : Route

    @Serializable
    @Parcelize
    data class Player(val url: String) : Route
}
