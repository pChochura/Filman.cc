package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.dto.ActorDetail
import com.pointlessapps.filman.data.api.dto.Genre
import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.dto.Movie
import com.pointlessapps.filman.data.api.dto.PaginatedResponse
import com.pointlessapps.filman.data.api.dto.TvShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class FilmanApi(private val client: OkHttpClient) {
    private val baseUrl = "http://10.0.2.2:8080"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getFeaturedAll(page: Int = 1): PaginatedResponse<MediaSummary> =
        get("/featured/all?page=$page")

    suspend fun getFeaturedMovies(page: Int = 1): PaginatedResponse<Movie> =
        get("/featured/movies?page=$page")

    suspend fun getFeaturedShows(page: Int = 1): PaginatedResponse<TvShow> =
        get("/featured/shows?page=$page")

    suspend fun search(query: String = "", genreId: Int? = null, page: Int = 1): PaginatedResponse<MediaSummary> {
        val url = StringBuilder("/search?page=$page")
        if (query.isNotEmpty()) url.append("&query=$query")
        if (genreId != null) url.append("&genreId=$genreId")
        return get(url.toString())
    }

    suspend fun getMovieDetails(id: Int): Movie = get("/movies/$id/details")
    suspend fun getMovieLinks(id: Int): LinksResponse = get("/movies/$id/links")
    suspend fun getTvShowDetails(id: Int): TvShow = get("/shows/$id/details")
    suspend fun getCategories(): List<Genre> = get("/search/categories")
    
    suspend fun getActorDetails(id: Int): ActorDetail = get("/actors/$id/details")
    suspend fun getActorMovies(id: Int): List<Movie> = get("/actors/$id/movies")


    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Unexpected code $response")
        }
        val body = response.body.string()
        json.decodeFromString(body)
    }
}
