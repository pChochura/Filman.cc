package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.CategoryInfo
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.MediaSource
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import android.util.Log

class FilmanRepository(private val api: FilmanApi) {
    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult {
        return try {
            api.getCategoryPage(path, page)
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            PageResult(emptyList(), emptyList(), path = path)
        }
    }

    fun searchMovies(query: String, prioritySource: MediaSource? = null): Flow<SearchResults> = flow {
        try {
            val results = api.searchMovies(query)
            emit(results)
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            emit(SearchResults(errorMessage = e.message))
        }
    }

    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? {
        return try {
            api.getMediaDetails(mediaUrlRaw)
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            null
        }
    }

    suspend fun getActorDetails(actorUrl: String, page: Int = 1): ActorDetails? {
        return try {
            api.getActorDetails(actorUrl, page)
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            null
        }
    }

    suspend fun getCategories(): List<FilterOption> {
        return try {
            api.getCategories()
        } catch (e: Exception) {
            Log.e("FilmanRepository", "Error during API call", e)
            emptyList()
        }
    }

    fun resolveFallbackUrl(url: String): String? = null
    fun invalidateMediaCache(url: String) {}
}
