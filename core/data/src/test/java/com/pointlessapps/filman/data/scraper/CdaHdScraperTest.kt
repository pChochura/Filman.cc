package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.local.CdaHdSessionManager
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.model.MediaSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CdaHdScraperTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var sessionManager: CdaHdSessionManager
    private lateinit var appSessionManager: SessionManager
    private lateinit var modelCache: ModelCache
    private lateinit var scraper: CdaHdScraper

    @Before
    fun setUp() {
        okHttpClient = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true) {
            every { cookieFlow } returns MutableStateFlow("cf_clearance=test1234")
        }
        appSessionManager = mockk(relaxed = true) {
            every { getUserAgent() } returns "TestAgent/1.0"
        }
        modelCache = mockk(relaxed = true)

        scraper = CdaHdScraper(
            okHttpClient = okHttpClient,
            sessionManager = sessionManager,
            appSessionManager = appSessionManager,
            modelCache = modelCache,
        )
    }

    private fun mockHttpResponse(html: String, code: Int = 200) {
        val call = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url("https://cda-hd.cc/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(html.toResponseBody("text/html".toMediaTypeOrNull()))
            .build()
        every { call.execute() } returns response
        every { okHttpClient.newCall(any()) } returns call
    }

    @Test
    fun testSearchMoviesParsesMoviesAndTvShows() = runTest {
        val sampleHtml = """
            <html>
                <body>
                    <div class="result-item">
                        <article class="item movies">
                            <div class="poster"><img src="/poster1.jpg" alt="Gladiator II" /></div>
                            <div class="title"><a href="/filmy/gladiator-ii-2024/">Gladiator II</a></div>
                            <div class="extra"><span class="date">2024</span></div>
                        </article>
                    </div>
                    <div class="result-item">
                        <article class="item tvshows">
                            <div class="poster"><img src="/poster2.jpg" alt="Szogun" /></div>
                            <div class="title"><a href="/seriale/shogun-2024/">Szogun / Shogun</a></div>
                            <div class="extra"><span class="date">2024</span></div>
                        </article>
                    </div>
                </body>
            </html>
        """.trimIndent()

        mockHttpResponse(sampleHtml)

        val results = scraper.searchMovies("Gladiator")

        assertEquals(1, results.movies.size)
        assertEquals(1, results.tvShows.size)

        val movie = results.movies.first()
        assertEquals("Gladiator II", movie.titlePl)
        assertEquals(2024, movie.year)
        assertEquals("https://cda-hd.cc/filmy/gladiator-ii-2024/", movie.url)
        assertEquals(MediaSource.CDA_HD, movie.source)

        val tvShow = results.tvShows.first()
        assertEquals("Szogun", tvShow.titlePl)
        assertEquals("Shogun", tvShow.titleEn)
        assertEquals("https://cda-hd.cc/seriale/shogun-2024/", tvShow.url)
        assertTrue(tvShow.isTvShow)
    }

    @Test
    fun testCloudflareChallengeTriggersSessionChallenge() = runTest {
        val challengeHtml = """
            <html>
                <head><title>Just a moment...</title></head>
                <body><div id="cf-browser-verification">Please wait</div></body>
            </html>
        """.trimIndent()

        mockHttpResponse(challengeHtml, code = 403)

        scraper.searchMovies("Test")

        verify { sessionManager.requestChallenge() }
    }
}
