package com.pointlessapps.filman.lite

import com.pointlessapps.filman.lite.data.LiteApi
import com.pointlessapps.filman.lite.data.LiteException
import com.pointlessapps.filman.lite.data.LiteRepository
import com.pointlessapps.filman.lite.data.MediaItem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LiteRepositoryTest {

    private fun repository(status: HttpStatusCode = HttpStatusCode.OK, handler: (String) -> String): LiteRepository {
        val engine = MockEngine { request ->
            respond(
                content = handler(request.url.encodedPathAndQuery),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return LiteRepository(LiteApi(client, { "http://test" }), pollDelayMs = 0)
    }

    @Test
    fun searchMapsResultsAndSkipsBlankQueries() = runTest {
        val repo = repository { _ ->
            """{"page":1,"results":[
                {"id":1,"title":"Movie","overview":"o","posterPath":"/p.jpg","mediaType":"MOVIE"},
                {"id":2,"title":"Show","overview":"o","mediaType":"TV_SHOW"}],"totalPages":1,"totalResults":2}"""
        }
        assertTrue(repo.search("  ").isEmpty())
        val results = repo.search("abc")
        assertEquals(listOf(false, true), results.map { it.isTvShow })
        assertEquals(LiteRepository.IMAGE_BASE_URL + "/p.jpg", results[0].posterUrl)
        assertEquals(null, results[1].posterUrl)
    }

    @Test
    fun sourcesPollsUntilReadyAndDropsWebViewLinks() = runTest {
        var calls = 0
        val repo = repository { _ ->
            calls++
            if (calls < 3) {
                """{"status":"PENDING"}"""
            } else {
                """{"status":"READY","links":[
                    {"url":"https://a/v.mp4","sourceWebsite":"A","quality":"720p"},
                    {"url":"https://b/embed","displayMode":"WEBVIEW"}]}"""
            }
        }
        val sources = repo.sources(MediaItem(1, "Movie", "", null, isTvShow = false))
        assertEquals(3, calls)
        assertEquals(1, sources.size)
        assertEquals("https://a/v.mp4", sources.single().url)
    }

    @Test
    fun sourcesSurfaceServerErrors() = runTest {
        val repo = repository(HttpStatusCode.InternalServerError) { """{"status":"ERROR","message":"boom"}""" }
        val error = assertFailsWith<LiteException> {
            repo.sources(MediaItem(1, "Movie", "", null, isTvShow = false))
        }
        assertEquals("boom", error.message)
    }

    @Test
    fun tvShowSourcesRequireEpisode() = runTest {
        val repo = repository { "{}" }
        assertFailsWith<LiteException> { repo.sources(MediaItem(1, "Show", "", null, isTvShow = true)) }
    }
}
