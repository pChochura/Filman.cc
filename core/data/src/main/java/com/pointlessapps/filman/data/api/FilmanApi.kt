package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.PageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class FilmanApi(private val client: OkHttpClient) {
    private val baseUrl = "http://10.0.2.2:8080"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getHighestRatedMovies(page: Int = 1): PageResult = get("/movies/highest-rated?page=$page")
    suspend fun getMostViewedMovies(page: Int = 1): PageResult = get("/movies/most-viewed?page=$page")
    suspend fun getRecentlyAddedMovies(page: Int = 1): PageResult = get("/movies/recently-added?page=$page")

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl}${path}").build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Unexpected code $response")
        val body = response.body?.string() ?: throw IOException("Empty body")
        json.decodeFromString(body)
    }
}
