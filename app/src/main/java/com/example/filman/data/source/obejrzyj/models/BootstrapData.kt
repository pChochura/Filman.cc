package com.example.filman.data.source.obejrzyj.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BootstrapData(
    val loaders: Loaders? = null
)

@Serializable
data class Loaders(
    val channelPage: ChannelPage? = null
)

@Serializable
data class ChannelPage(
    val channel: Channel? = null
)

@Serializable
data class Channel(
    val content: ChannelContent? = null
)

@Serializable
data class ChannelContent(
    val data: List<CategoryData> = emptyList()
)

@Serializable
data class CategoryData(
    val id: Int? = null,
    val name: String? = null,
    val slug: String? = null,
    val config: CategoryConfig? = null,
    val content: CategoryContent? = null
)

@Serializable
data class CategoryConfig(
    val nestedLayout: String? = null
)

@Serializable
data class CategoryContent(
    val data: List<ObejrzyjMovie> = emptyList()
)

@Serializable
data class ObejrzyjMovie(
    val id: Int? = null,
    val name: String? = null,
    val release_date: String? = null,
    val year: Int? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val is_series: Boolean? = null,
    val rating: Double? = null,
    val runtime: Int? = null,
    val description: String? = null,
    val primary_video: PrimaryVideo? = null
)

@Serializable
data class PrimaryVideo(
    val quality: String? = null,
    val language_type: String? = null
)
