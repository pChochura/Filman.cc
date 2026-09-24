package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.ActorDetails
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.FilterOption
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.model.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.util.Log
import kotlin.system.measureTimeMillis

class FilmanApi(private val client: OkHttpClient) {
    private val baseUrl = "http://10.0.2.2:8080"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun getCategoryPage(path: String, page: Int = 1): PageResult {
        // Because path can be arbitrary like 'filmy-online' or 'seriale-online', we can just use a generic endpoint on server 
        // Wait, the server uses explicit routes like /movies/recently-added. 
        // Let's create a generic endpoint on the server for arbitrary categories, or map them here.
        // Actually, on the server we can add a generic category route: GET /category?path=path&page=page
        return get("/category?path=$path&page=$page")
    }

    suspend fun getMoviesHighestRated(page: Int = 1): PageResult = get("/movies/highest-rated?page=$page")
    suspend fun getMoviesMostViewed(page: Int = 1): PageResult = get("/movies/most-viewed?page=$page")
    suspend fun getMoviesRecentlyAdded(page: Int = 1): PageResult = get("/movies/recently-added?page=$page")

    suspend fun searchMovies(query: String): SearchResults = get("/search?q=$query")
    suspend fun getCategories(): List<FilterOption> = get("/search/categories")

    // For actor and media details, we can add endpoints on the server!
    suspend fun getMediaDetails(mediaUrlRaw: String): DetailedMedia? = get("/media/url?url=$mediaUrlRaw")
    suspend fun getActorDetails(actorUrl: String, page: Int = 1): ActorDetails? = get("/actor/url?url=$actorUrl&page=$page")

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val url = "${baseUrl}${path}"
        Log.d("FilmanApi", "--> GET $url")
        val request = Request.Builder().url(url).build()
        var bodyStr: String? = null
        var statusCode = -1
        val time = measureTimeMillis {
            val response = client.newCall(request).execute()
            statusCode = response.code
            if (!response.isSuccessful) {
                Log.e("FilmanApi", "<-- $statusCode GET $url (Failed)")
                throw IOException("Unexpected code $response")
            }
            bodyStr = response.body?.string()
        }
        val body = bodyStr ?: throw IOException("Empty body")
        Log.d("FilmanApi", "<-- $statusCode GET $url (${time}ms, ${body.length}-byte body)")
        json.decodeFromString(body)
    }
}
