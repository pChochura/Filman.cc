package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.tmdb.TmdbApiClient
import com.pointlessapps.filman.data.model.MediaRef
import com.pointlessapps.filman.data.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

            val candidates = client.search("Title", "pl", tmdbId = 123)
            assertEquals(42, candidates.single().fileId)
            assertEquals("https://example.com/sub.srt", client.download(candidates.single().fileId).link)
            val search = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("123", search.queryParameter("tmdb_id"))
            assertEquals("pl", search.queryParameter("languages"))
            assertNull(search.queryParameter("query"))
            assertNull(search.queryParameter("parent_tmdb_id"))
            val download = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/download", download.requestUrl!!.encodedPath)
            assertEquals("""{"file_id":42}""", download.body.readUtf8())
        }
    }

    @Test
    fun `OpenSubtitles uses parent ID for episodes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            OpenSubtitlesClient(clientFor(server)).search("Title", "en", 2, 3, 123)
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
            OpenSubtitlesClient(clientFor(server)).search("Some title", "pl", 1, 4)
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("Some title", url.queryParameter("query"))
            assertEquals("1", url.queryParameter("season_number"))
            assertEquals("4", url.queryParameter("episode_number"))
            assertNull(url.queryParameter("tmdb_id"))
            assertNull(url.queryParameter("parent_tmdb_id"))
        }
    }

    @Test
    fun `search ranks full matching releases before popular or forced tracks without downloading`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"data":[
                    {"id":"1","attributes":{"subtitle_id":"1","language":"en","foreign_parts_only":true,"download_count":9999,"files":[{"file_id":1,"file_name":"forced.srt"}]}},
                    {"id":"2","attributes":{"subtitle_id":"2","language":"en","download_count":1000,"files":[{"file_id":2,"file_name":"other.srt"}]}},
                    {"id":"3","attributes":{"subtitle_id":"3","language":"en","release":"Movie.2024.1080p.WEB-DL","download_count":2,"hearing_impaired":true,"files":[{"file_id":3,"file_name":"Movie.2024.1080p.WEB-DL.srt"}]}}
                ]}""",
            ))
            val results = OpenSubtitlesClient(clientFor(server))
                .search("Movie", "en", tmdbId = 123, videoFileName = "Movie.2024.1080p.WEB-DL.mkv")
            assertEquals(listOf(3, 2, 1), results.map { it.fileId })
            assertEquals(true, results.first().hearingImpaired)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `search prefers matching release group and source format over popular mismatched track`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"data":[
                    {"id":"1","attributes":{"subtitle_id":"1","language":"pl","release":"Gladiator.II.2024.1080p.BluRay.x264-SPARKS","download_count":50000,"files":[{"file_id":10,"file_name":"Gladiator.II.2024.1080p.BluRay.x264-SPARKS.srt"}]}},
                    {"id":"2","attributes":{"subtitle_id":"2","language":"pl","release":"Gladiator.II.2024.720p.WEB-DL.x264-FLUX","download_count":100,"files":[{"file_id":20,"file_name":"Gladiator.II.2024.720p.WEB-DL.x264-FLUX.srt"}]}}
                ]}""",
            ))
            val results = OpenSubtitlesClient(clientFor(server))
                .search("Gladiator II", "pl", tmdbId = 123, videoFileName = "Gladiator.II.2024.1080p.WEB-DL.DDP5.1.Atmos.H.264-FLUX.mkv")
            // Candidate 20 (FLUX + WEB-DL) beats Candidate 10 (BluRay + SPARKS) despite lower downloads!
            assertEquals(20, results.first().fileId)
            assertEquals(10, results.last().fileId)
        }
    }

    @Test
    fun `search prioritizes moviehash match above all else`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"data":[
                    {"id":"1","attributes":{"subtitle_id":"1","language":"pl","download_count":50000,"files":[{"file_id":1,"file_name":"popular.srt"}]}},
                    {"id":"2","attributes":{"subtitle_id":"2","language":"pl","moviehash_match":true,"download_count":1,"files":[{"file_id":2,"file_name":"hash_match.srt"}]}}
                ]}""",
            ))
            val results = OpenSubtitlesClient(clientFor(server))
                .search("Movie", "pl", tmdbId = 123, videoFileName = "Movie.mkv")
            assertEquals(2, results.first().fileId)
        }
    }

    @Test
    fun `search queries moviehash first and falls back to tmdbId when hash returns empty`() = runBlocking {
        MockWebServer().use { server ->
            // First request: moviehash search returns empty
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            // Second request: fallback search returns data
            server.enqueue(MockResponse().setBody(
                """{"data":[{"id":"1","attributes":{"subtitle_id":"1","language":"pl","files":[{"file_id":99,"file_name":"fallback.srt"}]}}]}""",
            ))
            val results = OpenSubtitlesClient(clientFor(server))
                .search("Movie", "pl", tmdbId = 123, videoFileName = "Movie.mkv", movieHash = "0123456789abcdef")

            assertEquals(1, results.size)
            assertEquals(99, results.first().fileId)
            assertEquals(2, server.requestCount)

            val firstReq = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("0123456789abcdef", firstReq.queryParameter("moviehash"))

            val secondReq = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("123", secondReq.queryParameter("tmdb_id"))
            assertNull(secondReq.queryParameter("moviehash"))
        }
    }

    @Test
    fun `search and download surface HTTP failures`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(429))
            val client = OpenSubtitlesClient(clientFor(server))
            assertThrows(java.io.IOException::class.java) { runBlocking { client.search("Title", "en") } }
            assertThrows(java.io.IOException::class.java) { runBlocking { client.download(1) } }
        }
    }

    @Test
    fun `Wyzie uses provided IMDb ID directly without TMDB lookup`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val tmdb = mockk<TmdbApiClient>()
            WyzieSubsClient(clientFor(server), tmdb).searchSubtitles("Title", 2024, 2, 3, tmdbId = 123, imdbId = "tt1234567", language = "pl")
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("tt1234567", url.queryParameter("id"))
            assertEquals("2", url.queryParameter("season"))
            assertEquals("3", url.queryParameter("episode"))
            assertEquals("pl", url.queryParameter("language"))
            assertNull(url.queryParameter("s"))
            assertNull(url.queryParameter("e"))
            coVerify(exactly = 0) { tmdb.findTmdbId(any(), any(), any()) }
            coVerify(exactly = 0) { tmdb.getImdbId(any()) }
        }
    }

    @Test
    fun `Wyzie resolves TMDB ID to IMDb ID when IMDb ID is missing`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val tmdb = mockk<TmdbApiClient>()
            coEvery { tmdb.getImdbId(MediaRef(MediaType.TV, 123)) } returns "tt9999999"
            WyzieSubsClient(clientFor(server), tmdb).searchSubtitles("Title", 2024, 2, 3, 123)
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("tt9999999", url.queryParameter("id"))
            assertEquals("2", url.queryParameter("season"))
            assertEquals("3", url.queryParameter("episode"))
            assertNull(url.queryParameter("language"))
            coVerify(exactly = 0) { tmdb.findTmdbId(any(), any(), any()) }
            coVerify(exactly = 1) { tmdb.getImdbId(MediaRef(MediaType.TV, 123)) }
        }
    }

    @Test
    fun `Wyzie resolves title when ID is missing`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            val tmdb = mockk<TmdbApiClient>()
            coEvery { tmdb.findTmdbId("Title", 2024, false) } returns 456
            coEvery { tmdb.getImdbId(MediaRef(MediaType.MOVIE, 456)) } returns "tt4567890"
            WyzieSubsClient(clientFor(server), tmdb).searchSubtitles("Title", 2024, null, null)
            val url = server.takeRequest(5, TimeUnit.SECONDS)!!.requestUrl!!
            assertEquals("tt4567890", url.queryParameter("id"))
            assertNull(url.queryParameter("season"))
            assertNull(url.queryParameter("episode"))
            coVerify(exactly = 1) { tmdb.findTmdbId("Title", 2024, false) }
            coVerify(exactly = 1) { tmdb.getImdbId(MediaRef(MediaType.MOVIE, 456)) }
        }
    }

    @Test
    fun `rankWyziePolishCandidates prefers human non-HI over AI-translated and HI subtitles`() {
        val aiSub = WyzieSubtitleResponse(
            id = "1",
            url = "https://sub.wyzie.io/ai.srt",
            display = "Polish (AI)",
            language = "pl",
            downloadCount = 5000,
            ai = true,
        )
        val hiSub = WyzieSubtitleResponse(
            id = "2",
            url = "https://sub.wyzie.io/hi.srt",
            display = "Polish HI",
            language = "pl",
            downloadCount = 5000,
            isHearingImpaired = true,
        )
        val humanSub = WyzieSubtitleResponse(
            id = "3",
            url = "https://sub.wyzie.io/human.srt",
            display = "Polish",
            language = "pl",
            downloadCount = 10,
            ai = false,
            isHearingImpaired = false,
        )

        val ranked = rankWyziePolishCandidates(listOf(aiSub, hiSub, humanSub), videoFileName = null)
        assertEquals(listOf("3", "2", "1"), ranked.map { it.id })
    }

    @Test
    fun `OpenSubtitles prefers non-HI over HI when release metadata is otherwise equal`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"data":[
                    {"id":"1","attributes":{"subtitle_id":"1","language":"pl","release":"Movie.2024.1080p.WEB-DL","download_count":500,"hearing_impaired":true,"files":[{"file_id":10,"file_name":"Movie.2024.1080p.WEB-DL.HI.srt"}]}},
                    {"id":"2","attributes":{"subtitle_id":"2","language":"pl","release":"Movie.2024.1080p.WEB-DL","download_count":10,"hearing_impaired":false,"files":[{"file_id":20,"file_name":"Movie.2024.1080p.WEB-DL.srt"}]}}
                ]}""",
            ))
            val results = OpenSubtitlesClient(clientFor(server))
                .search("Movie", "pl", tmdbId = 123, videoFileName = "Movie.2024.1080p.WEB-DL.mkv")
            assertEquals(listOf(20, 10), results.map { it.fileId })
        }
    }
}
