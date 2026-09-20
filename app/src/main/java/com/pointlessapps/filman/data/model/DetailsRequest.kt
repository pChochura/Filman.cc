package com.pointlessapps.filman.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
sealed interface DetailsRequest : Parcelable {
    @Serializable
    @Parcelize
    data class Url(val url: String) : DetailsRequest

    @Serializable
    @Parcelize
    data class Search(val title: String, val year: Int?, val isTvShow: Boolean) : DetailsRequest
}
