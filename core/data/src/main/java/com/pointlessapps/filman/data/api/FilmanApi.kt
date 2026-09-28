package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.config.FilmanConfig
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
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.local.ZaluknijSessionManager
import okhttp3.Request
import java.io.IOException

class FilmanApi(
    private val client: OkHttpClient,
    private val sessionManager: SessionManager,
    private val zaluknijSessionManager: ZaluknijSessionManager,
) {
    private val baseUrl = "http://10.0.2.2:8080"
    private val json = Json {
        ignoreUnknownKeys = true
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
    suspend fun getTvShowLinks(id: Int): LinksResponse = get("/shows/$id/links")
    suspend fun getCategories(): List<Genre> = get("/search/categories")
    
    suspend fun getActorDetails(id: Int): ActorDetail = get("/actors/$id/details")
    suspend fun getActorMovies(id: Int): List<Movie> = get("/actors/$id/movies")

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val url = "$baseUrl$path"
        val requestBuilder = Request.Builder().url(url)
        val filmanCookie = sessionManager.getCookie()
        if (!filmanCookie.isNullOrEmpty()) {
            requestBuilder.header("X-Filman-Cookie", filmanCookie)
        }
        val zaluknijCookie = zaluknijSessionManager.getCookie()
        if (!zaluknijCookie.isNullOrEmpty()) {
            requestBuilder.header("X-Zaluknij-Cookie", zaluknijCookie)
        }
        val cloudflareCookie = NetworkClient.getAllCloudflareCookies()
        if (cloudflareCookie.isNotEmpty()) {
            requestBuilder.header("X-Cloudflare-Cookie", cloudflareCookie)
            requestBuilder.header("Cookie", cloudflareCookie)
        }
        val userAgent = sessionManager.getUserAgent()
        if (userAgent.isNotEmpty()) {
            requestBuilder.header("User-Agent", userAgent)
            requestBuilder.header("X-User-Agent", userAgent)
        }
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                if (resp.code == 401) {
                    val bodyString = try { resp.body.string() } catch (e: Exception) { "" }
                    val message = try {
                        json.decodeFromString<LinksResponse>(bodyString).message
                    } catch (e: Exception) {
                        null
                    }
                    handleUnauthorized(message)
                }
                throw IOException("Unexpected code $resp")
            }
            val body = resp.body.string()
            val result = json.decodeFromString<T>(body)
            if (result is LinksResponse && result.status == "NEEDS_AUTHORIZATION") {
                handleUnauthorized(result.message)
            }
            result
        }
    }

    private fun handleUnauthorized(message: String? = null) {
        val isChallengeActive = try { sessionManager.isChallengeActive.value } catch (_: Throwable) { false }
        
        if (message?.contains("Cloudflare", ignoreCase = true) == true && !isChallengeActive) {
            throw CloudflareChallengeDetected(
                domain = FilmanConfig.DOMAIN,
                url = FilmanConfig.BASE_URL,
                message = "Cloudflare challenge detected"
            )
        }
        
        throw AuthException(message ?: "Filman session expired, please log in again")
    }
}
