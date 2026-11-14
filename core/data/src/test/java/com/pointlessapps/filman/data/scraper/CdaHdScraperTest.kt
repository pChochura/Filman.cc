package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.cache.ModelCache
import com.pointlessapps.filman.data.api.CloudflareChallengeDetected
import com.pointlessapps.filman.data.api.extractors.VoeExtractor
import com.pointlessapps.filman.data.api.extractors.getExtractorForUrl
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

        verify { sessionManager.requestChallenge(any()) }
    }

    @Test
    fun testPlain403DoesNotTriggerChallenge() = runTest {
        mockHttpResponse("<html>Forbidden</html>", code = 403)

        scraper.searchMovies("Test")

        verify(exactly = 0) { sessionManager.requestChallenge(any()) }
    }

    @Test
    fun testMovieplayIframeIsReturnedButUnrelatedIframeIsIgnored() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = if (request.url.encodedPath == "/") {
                """<div class="result-item"><div class="title">
                    <a href="/filmy/example/">Example</a></div></div>"""
            } else {
                """<iframe src="https://ads.example/banner"></iframe>
                    <div id="player2"><div class="movieplay">
                    <iframe src="//video.example/embed/1"></iframe></div></div>"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body.toResponseBody("text/html".toMediaTypeOrNull())).build()
        }.build()
        val source = CdaHdScraper(client, sessionManager, appSessionManager, modelCache)
        val embeds = source.getEmbeds("Example", null)
        assertEquals(1, embeds.size)
        assertEquals("https://video.example/embed/1", embeds.single().url)
        assertTrue(embeds.single().isWebView)
    }

    @Test
    fun `series searches original title and reads streaming episode player`() = runTest {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val query = request.url.queryParameter("s")
            requests.add(if (query != null) "search:$query" else request.url.encodedPath)
            val body = when {
                query == "Polski: tytuł & nazwa" -> "<html></html>"
                query == "Original Show" -> """<div class="items"><div class="item">
                    <a href="/seriale/original-show/"><div class="image">7.3</div></a>
                    <div class="boxinfo"><a href="/seriale/original-show/">
                    <span class="tt">Original Show</span><span class="ttx">Description</span>
                    </a></div></div></div>"""
                request.url.encodedPath == "/seriale/original-show/" -> """
                    <div id="seasons"><div class="se-c"><div class="se-t">1</div>
                    <ul class="episodios"><li><div class="numerando">1 x 1</div>
                    <div class="episodiotitle"><a href="/odcinki/original-show-1x1/">Episode</a></div>
                    </li></ul></div></div>"""
                request.url.encodedPath == "/odcinki/original-show-1x1/" -> """
                    <iframe src="https://ads.example/banner"></iframe>
                    <div id="streaming"><div class="player2"><div class="embed2">
                    <div id="player23"><iframe src="//video.example/show/1"></iframe></div>
                    </div></div></div>"""
                else -> error("Unexpected request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body.toResponseBody("text/html".toMediaTypeOrNull())).build()
        }.build()
        val source = CdaHdScraper(client, sessionManager, appSessionManager, modelCache)
        val embeds = source.getEmbeds("Polski: tytuł & nazwa", null, 1, 1, "Original Show")
        assertEquals(
            listOf("search:Polski: tytuł & nazwa", "search:Original Show", "/seriale/original-show/", "/odcinki/original-show-1x1/"),
            requests,
        )
        assertEquals("https://video.example/show/1", embeds.single().url)
        assertEquals("cda-hd.cc", embeds.single().sourceWebsite)
        assertTrue(embeds.single().isWebView)
    }

    @Test
    fun `episode reads player links and iframes without treating navigation or scripts as sources`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/" -> """<div class="result-item"><div class="title">
                    <a href="/seriale/example/">Example</a></div></div>"""
                "/seriale/example/" -> """<div id="seasons"><div class="se-c"><div class="se-t">1</div>
                    <ul class="episodios"><li><div class="numerando">1 x 1</div>
                    <div class="episodiotitle"><a href="/episode/example/">Episode</a></div>
                    </li></ul></div></div>"""
                "/episode/example/" -> """
                    <div id="streaming"><div class="player2"><div class="embed2">
                    <div id="player21"><a href="https://chuckle-tube.com/e/abc"><img src="/preview.jpg"></a>
                    <span class="tit">Lektor PL</span></div>
                    <div id="player22"><script src="https://player.cda-h.co/player/hash.php?hash=123"></script></div>
                    <div id="player23"><iframe src="//vtbe.to/embed-abc.html"></iframe></div>
                    <div id="player24"><iframe data-src="/embed/third"></iframe></div>
                    </div><div class="navplayer2"><a href="https://ads.example/">ad</a></div></div></div>"""
                else -> error("Unexpected request")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body.toResponseBody("text/html".toMediaTypeOrNull())).build()
        }.build()
        val source = CdaHdScraper(client, sessionManager, appSessionManager, modelCache)
        val embeds = source.getEmbeds("Example", null, 1, 1)

        assertEquals(
            listOf("https://chuckle-tube.com/e/abc", "https://vtbe.to/embed-abc.html", "https://cda-hd.cc/embed/third"),
            embeds.map { it.url },
        )
        assertEquals("Lektor PL", embeds.first().version)
        assertEquals("Voe", embeds.first().serverName)
        assertEquals(VoeExtractor, getExtractorForUrl(embeds.first().url, embeds.first().serverName))
        assertTrue(embeds.drop(1).all { it.isWebView })
    }

    @Test
    fun `missing season does not fall back to another season or the show page`() = runTest {
        val paths = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            paths.add(request.url.encodedPath)
            val body = if (request.url.encodedPath == "/") {
                """<div class="result-item"><div class="title">
                    <a href="/seriale/example/">Example</a></div></div>"""
            } else {
                """<div id="seasons"><div class="se-c"><div class="se-t">1</div>
                    <ul class="episodios"><li><div class="numerando">1 x 1</div>
                    <div class="episodiotitle"><a href="/odcinki/example-1x1/">Episode</a></div>
                    </li></ul></div></div>"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(body.toResponseBody("text/html".toMediaTypeOrNull())).build()
        }.build()
        val source = CdaHdScraper(client, sessionManager, appSessionManager, modelCache)
        assertTrue(source.getEmbeds("Example", null, 2, 1).isEmpty())
        assertEquals(listOf("/", "/seriale/example/"), paths)
    }

    @Test
    fun `player ajax challenge is reported rather than silently treated as no links`() = runTest {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val isAjax = request.url.encodedPath == "/wp-admin/admin-ajax.php"
            val body = when (request.url.encodedPath) {
                "/" -> """<div class="result-item"><div class="title">
                    <a href="/filmy/example/">Example</a></div></div>"""
                "/filmy/example/" -> """<ul id="playeroptionsul">
                    <li data-post="1" data-nume="1" data-type="movie"></li></ul>"""
                else -> "<html>Challenge</html>"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (isAjax) 403 else 200).message("test")
                .apply { if (isAjax) header("cf-mitigated", "challenge") }
                .body(body.toResponseBody("text/html".toMediaTypeOrNull())).build()
        }.build()
        val source = CdaHdScraper(client, sessionManager, appSessionManager, modelCache)
        val failure = runCatching { source.getEmbeds("Example", null) }.exceptionOrNull()
        assertTrue(failure is CloudflareChallengeDetected)
        verify { sessionManager.requestChallenge(any()) }
    }
}
