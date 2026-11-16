package com.pointlessapps.filman.data.api

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubtitleSearchTest {
    private fun clientFor(server: MockWebServer) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = server.url(request.url.encodedPath).newBuilder()
                .encodedQuery(request.url.encodedQuery)
                .build()
            chain.proceed(request.newBuilder().url(url).build())
        }
        .build()

    @Test
    fun `OpenSubtitles uses movie ID and downloads result`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"data":[{"id":"1","attributes":{"subtitle_id":"1","language":"pl","files":[{"file_id":42,"file_name":"test.srt"}]}}]}""",
            ))
            server.enqueue(MockResponse().setBody("""{"link":"https://example.com/sub.srt","file_name":"test.srt"}"""))
            val client = OpenSubtitlesClient(clientFor(server))

            assertEquals("https://example.com/sub.srt", client.searchAndDownload("Title", "pl", tmdbId = 123))
            val search = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("123", search.queryParameter("tmdb_id"))
            assertEquals("pl", search.queryParameter("languages"))
            assertNull(search.queryParameter("query"))
            assertNull(search.queryParameter("parent_tmdb_id"))
            assertEquals("/api/v1/download", server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!.encodedPath)
        }
    }

    @Test
    fun `OpenSubtitles uses parent ID for episodes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            OpenSubtitlesClient(clientFor(server)).searchAndDownload("Title", "en", 2, 3, 123)
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("123", url.queryParameter("parent_tmdb_id"))
            assertEquals("2", url.queryParameter("season_number"))
            assertEquals("3", url.queryParameter("episode_number"))
            assertEquals("en", url.queryParameter("languages"))
            assertNull(url.queryParameter("tmdb_id"))
            assertNull(url.queryParameter("query"))
        }
    }

    @Test
    fun `OpenSubtitles falls back to title when ID is missing`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            OpenSubtitlesClient(clientFor(server)).searchAndDownload("Some title", "pl", 1, 4)
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("Some title", url.queryParameter("query"))
            assertEquals("1", url.queryParameter("season_number"))
            assertEquals("4", url.queryParameter("episode_number"))
            assertNull(url.queryParameter("tmdb_id"))
            assertNull(url.queryParameter("parent_tmdb_id"))
        }
    }

    @Test
    fun `Wyzie uses existing series ID without title lookup`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val tmdb = mockk<TmdbClient>()
            WyzieSubsClient(clientFor(server), tmdb).searchSubtitles("Title", 2024, 2, 3, 123)
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("123", url.queryParameter("id"))
            assertEquals("2", url.queryParameter("s"))
            assertEquals("3", url.queryParameter("e"))
            coVerify(exactly = 0) { tmdb.getTmdbId(any(), any(), any()) }
        }
    }

    @Test
    fun `Wyzie resolves title when ID is missing`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val tmdb = mockk<TmdbClient>()
            coEvery { tmdb.getTmdbId("Title", 2024, false) } returns "456"
            WyzieSubsClient(clientFor(server), tmdb).searchSubtitles("Title", 2024, null, null)
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("456", url.queryParameter("id"))
            assertNull(url.queryParameter("s"))
            coVerify(exactly = 1) { tmdb.getTmdbId("Title", 2024, false) }
        }
    }
}
