package com.pointlessapps.filman.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface DetailsRequest : Parcelable {
    val mediaRef: MediaRef?

    @Serializable
    @Parcelize
    data class Url(
        val url: String,
        val isTvShow: Boolean = url.startsWith("show_"),
    ) : DetailsRequest {
        override val mediaRef: MediaRef?
            get() = MediaRef.parse(url, isTvShow)
    }

    @Serializable
    @Parcelize
    data class ByRef(
        val ref: MediaRef,
    ) : DetailsRequest {
        override val mediaRef: MediaRef
            get() = ref
    }
}
