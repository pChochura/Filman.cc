package com.pointlessapps.filman.data.sync

import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.sync.model.SyncAuthRequest
import com.pointlessapps.filman.data.sync.model.SyncPushRequest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SyncApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SyncApiClient(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `login posts credentials to api auth login and parses token`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"jwt_123","user":{"id":"user_1","username":"tester"},"success":true}"""),
        )

        val result = client.login(SyncAuthRequest("tester", "password123"))
        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("jwt_123", response.token)
        assertEquals("tester", response.user?.username)

        val request = server.takeRequest()
        assertEquals("/api/auth/login", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("password123"))
    }

    @Test
    fun `login returns failure on 401 error response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"success":false,"message":"Invalid credentials"}"""),
        )

        val result = client.login(SyncAuthRequest("wrong", "credentials"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid credentials") == true)
    }

    @Test
    fun `register posts credentials to api auth register and parses response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"token":"new_user_token","user":{"id":"u2","username":"newguy"},"success":true}"""),
        )

        val result = client.register(SyncAuthRequest("newguy", "secret"))
        assertTrue(result.isSuccess)
        assertEquals("new_user_token", result.getOrThrow().token)

        val request = server.takeRequest()
        assertEquals("/api/auth/register", request.path)
    }

    @Test
    fun `pull sends since parameter and auth header and parses response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "serverTimestamp": 1700000000,
                        "progress": [
                            {
                                "type": "MOVIE",
                                "tmdbId": 123,
                                "titlePl": "Test Movie",
                                "progressPercentage": 0.5,
                                "progressMs": 30000,
                                "isWatched": false,
                                "updatedAt": 1699999999
                            }
                        ],
                        "watchlist": [],
                        "favorites": [],
                        "preferences": {"preferred_quality": "1080p"}
                    }
                    """.trimIndent(),
                ),
        )

        val result = client.pull(1690000000L, "my_token")
        assertTrue(result.isSuccess)
        val pullResponse = result.getOrThrow()
        assertEquals(1700000000L, pullResponse.serverTimestamp)
        assertEquals(1, pullResponse.progress.size)
        assertEquals(123, pullResponse.progress.first().tmdbId)
        assertEquals("1080p", pullResponse.preferences["preferred_quality"])

        val request = server.takeRequest()
        assertEquals("/api/sync/pull?since=1690000000", request.path)
        assertEquals("Bearer my_token", request.getHeader("Authorization"))
    }

    @Test
    fun `pull handles server response with only url and without type or tmdbId`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "serverTimestamp": 1700000000,
                        "progress": [
                            {
                                "url": "show_1399_1_2",
                                "parentUrl": "show_1399",
                                "titlePl": "Gra o tron",
                                "season": 1,
                                "episode": 2,
                                "hasNextEpisode": true,
                                "progressPercentage": 0.8,
                                "progressMs": 45000,
                                "isWatched": true,
                                "updatedAt": 1699999999
                            }
                        ],
                        "watchlist": [
                            {
                                "url": "movie_550",
                                "titlePl": "Fight Club",
                                "isTvShow": false,
                                "year": 1999,
                                "isDeleted": false,
                                "updatedAt": 1699999999
                            }
                        ],
                        "favorites": [
                            {
                                "url": "show_1399",
                                "titlePl": "Gra o tron",
                                "isTvShow": true,
                                "isDeleted": true,
                                "updatedAt": 1699999999
                            }
                        ],
                        "preferences": {}
                    }
                    """.trimIndent(),
                ),
        )

        val result = client.pull(0L, "my_token")
        assertTrue(result.isSuccess)
        val pullResponse = result.getOrThrow()
        assertEquals(1, pullResponse.progress.size)
        val progressEntry = pullResponse.progress.first()
        assertEquals("show_1399_1_2", progressEntry.url)
        val mediaRef = progressEntry.resolveMediaRef()
        assertEquals(1399, mediaRef?.tmdbId)
        assertEquals(1, mediaRef?.season)
        assertEquals(2, mediaRef?.episode)
        assertTrue(progressEntry.hasNextEpisode)

        assertEquals(1, pullResponse.watchlist.size)
        val watchlistEntry = pullResponse.watchlist.first()
        assertEquals(550, watchlistEntry.resolveMediaRef()?.tmdbId)

        assertEquals(1, pullResponse.favorites.size)
        val favoritesEntry = pullResponse.favorites.first()
        assertTrue(favoritesEntry.isDeleted)
        assertEquals(1399, favoritesEntry.resolveMediaRef()?.tmdbId)
    }

    @Test
    fun `push posts payload and parses server response`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"serverTimestamp":1700000500,"success":true}"""),
        )

        val result = client.push(SyncPushRequest(clientTimestamp = 1699999999L), "my_token")
        assertTrue(result.isSuccess)
        assertEquals(1700000500L, result.getOrThrow().serverTimestamp)

        val request = server.takeRequest()
        assertEquals("/api/sync/push", request.path)
        assertEquals("Bearer my_token", request.getHeader("Authorization"))
    }
}
