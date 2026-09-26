package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.dto.MediaSummary
import com.pointlessapps.filman.data.api.dto.Movie
import com.pointlessapps.filman.data.api.dto.PaginatedResponse
import com.pointlessapps.filman.data.api.dto.TvShow
import com.pointlessapps.filman.data.api.dto.LinksResponse

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

    suspend fun search(query: String, page: Int = 1): PaginatedResponse<MediaSummary> =
        get("/search?query=$query&page=$page")

    suspend fun getMovieDetails(id: Int): Movie = get("/movies/$id/details")
    suspend fun getMovieLinks(id: Int): LinksResponse = get("/movies/$id/links")
    suspend fun getTvShowDetails(id: Int): TvShow = get("/shows/$id/details")


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
