package com.pointlessapps.filman.lite.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import com.pointlessapps.filman.lite.FilmanCredentials
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

private val errorJson = Json { ignoreUnknownKeys = true }

internal class LiteApi(
    private val client: HttpClient,
    private val baseUrl: () -> String,
    private val credentials: () -> FilmanCredentials? = { null },
) {

    suspend fun search(query: String, page: Int = 1): PaginatedResponse<MediaSummaryDto> =
        get("/search") {
            parameter("query", query)
            parameter("page", page)
        }

    suspend fun tvShow(id: Int): TvShowDto = get("/shows/$id/details")

    suspend fun movieLinks(id: Int): LinksResponseDto = get("/movies/$id/links")

    suspend fun tvShowLinks(id: Int, season: Int, episode: Int): LinksResponseDto =
        get("/shows/$id/links") {
            parameter("season", season)
            parameter("episode", episode)
        }

    private suspend inline fun <reified T> get(
        path: String,
        crossinline block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): T {
        val saved = credentials()
        val response = client.get(baseUrl() + path) {
            block()
            if (saved != null) {
                header("X-Filman-Cookie", saved.cookie)
                if (saved.userAgent.isNotBlank()) {
                    header("User-Agent", saved.userAgent)
                    header("X-User-Agent", saved.userAgent)
                }
            }
        }
        if (!response.status.isSuccess()) {
            val message = runCatching {
                errorJson
                    .decodeFromString<LinksResponseDto>(response.bodyAsText()).message
            }.getOrNull()
            if (response.status.value == 401) {
                throw AuthRequiredException(message ?: "Log in to Filman to load sources")
            }
            throw LiteException(message ?: "Server error (${response.status.value})")
        }
        return response.body()
    }
}
