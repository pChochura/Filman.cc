package com.pointlessapps.filman.lite

import com.pointlessapps.filman.lite.data.AuthRequiredException
import com.pointlessapps.filman.lite.data.LiteApi
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
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FilmanSessionTest {

    @Test
    fun savedCredentialsSurviveReloadAndClearRemovesThem() {
        val fs = FakeFileSystem()
        val dir = "/data".also { fs.createDirectories(it.toPath()) }
        FilmanSession(dir, fs).save("PHPSESSID=abc; x=1", "UA/1.0")

        val reloaded = FilmanSession(dir, fs)
        assertEquals(FilmanCredentials("PHPSESSID=abc; x=1", "UA/1.0"), reloaded.credentials.value)

        reloaded.clear()
        assertNull(reloaded.credentials.value)
        assertNull(FilmanSession(dir, fs).credentials.value)
    }

    @Test
    fun apiSendsCookieHeadersAndMapsUnauthorized() = runTest {
        var cookieHeader: String? = null
        var status = HttpStatusCode.OK
        val engine = MockEngine { request ->
            cookieHeader = request.headers["X-Filman-Cookie"]
            respond(
                content = if (status == HttpStatusCode.OK) {
                    """{"page":1,"results":[],"totalPages":1,"totalResults":0}"""
                } else {
                    """{"status":"NEEDS_AUTHORIZATION","message":"expired"}"""
                },
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val credentials = FilmanCredentials("PHPSESSID=abc", "UA/1.0")
        val repo = LiteRepository(LiteApi(client, { "http://test" }, { credentials }), pollDelayMs = 0)

        repo.search("x")
        assertEquals("PHPSESSID=abc", cookieHeader)

        status = HttpStatusCode.Unauthorized
        val error = assertFailsWith<AuthRequiredException> { repo.sources(MediaItem(1, "m", "", null, false)) }
        assertEquals("expired", error.message)
    }
}
