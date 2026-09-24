package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FilmanRepository {
    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult {
        // TODO: Call API endpoint
        return PageResult(emptyList(), emptyList(), path = path)
        fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}

    fun searchMovies(query: String, prioritySource: MediaSource? = null): Flow<SearchResults> {
        // TODO: Call API endpoint
        return flowOf(SearchResults())
        fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}

    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? {
        // TODO: Call API endpoint
        return null
        fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}

    suspend fun getActorDetails(actorUrl: String, page: Int = 1): com.pointlessapps.filman.data.model.ActorDetails? {
        // TODO: Call API endpoint
        return null
        fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}

    suspend fun getCategories(): List<FilterOption> {
        // TODO: Call API endpoint
        return emptyList()
        fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}
    fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}
