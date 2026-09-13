package com.pointlessapps.filman.data.scraper.extractors

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Base64

class ExtractorsTest {

    @Test
    fun testGetExtractorForUrlResolvesVidsrc() {
        val urls = listOf(
            "https://vidsrc-embed.ru/embed/movie?tmdb=550",
            "https://vsembed.ru/embed/tv?tmdb=1399&season=1&episode=1",
            "https://vidsrc.to/embed/movie/550",
            "https://vidsrc.me/embed/550",
            "https://vidsrc.ru/movie/550",
        )
        for (url in urls) {
            val extractor = getExtractorForUrl(url)
            assertEquals("Expected VidsrcExtractor for $url", VidsrcExtractor, extractor)
        }

        val byServerName =
            getExtractorForUrl("https://example.com/stream", serverName = "VidSrc Server")
        assertEquals(VidsrcExtractor, byServerName)
    }

    @Test
    fun testGetExtractorForUrlResolvesVidnest() {
        val urls = listOf(
            "https://vidnest.io/embed/abc123",
            "https://vidnest.fun/embed/xyz789",
        )
        for (url in urls) {
            val extractor = getExtractorForUrl(url)
            assertEquals("Expected VidnestExtractor for $url", VidnestExtractor, extractor)
        }

        val byServerName = getExtractorForUrl("https://example.com/stream", serverName = "VidNest")
        assertEquals(VidnestExtractor, byServerName)
    }

    @Test
    fun testGetExtractorForUrlResolvesVidcore() {
        val urls = listOf(
            "https://vidcore.net/movie/508442",
            "https://vidcore.io/tv/1399/1/1",
        )
        for (url in urls) {
            val extractor = getExtractorForUrl(url)
            assertEquals("Expected VidcoreExtractor for $url", VidcoreExtractor, extractor)
        }

        val byServerName = getExtractorForUrl("https://example.com/stream", serverName = "VidCore")
        assertEquals(VidcoreExtractor, byServerName)
    }

    @Test
    fun testGetExtractorForUrlResolvesVidfast() {
        val urls = listOf(
            "https://vidfast.pro/movie/508442",
            "https://vidfast.vc/tv/1399/1/1",
        )
        for (url in urls) {
            val extractor = getExtractorForUrl(url)
            assertEquals("Expected VidfastExtractor for $url", VidfastExtractor, extractor)
        }

        val byServerName = getExtractorForUrl("https://example.com/stream", serverName = "VidFast")
        assertEquals(VidfastExtractor, byServerName)
    }

    @Test
    fun testGetExtractorForUrlResolvesVideasy() {
        val urls = listOf(
            "https://player.videasy.net/movie/550",
            "https://player.videasy.net/tv/1399/1/1",
            "https://api.speedracelight.com/cdn/sources-with-title?tmdbId=550&mediaType=movie",
        )
        for (url in urls) {
            val extractor = getExtractorForUrl(url)
            assertEquals("Expected VideasyExtractor for $url", VideasyExtractor, extractor)
        }

        val byServerName =
            getExtractorForUrl("https://example.com/stream", serverName = "Videasy (Breach)")
        assertEquals(VideasyExtractor, byServerName)
    }

    @Test
    fun testVidsrcNdonCipher() {
        // Chunk size 3 reversed
        val input = "abcdef"
        val expected = "defabc"
        assertEquals(expected, VidsrcExtractor.ndonQLf1Tzyx7bMG(input))
    }

    @Test
    fun testVidsrcO2VSCipher() {
        // Shift -3
        val input = "khoor"
        val expected = "hello"
        assertEquals(expected, VidsrcExtractor.o2VSUnjnZl(input))

        val mixed = "KHOOR zruog"
        val expectedMixed = "HELLO world"
        assertEquals(expectedMixed, VidsrcExtractor.o2VSUnjnZl(mixed))
    }

    @Test
    fun testVidsrcIhWrCipher() {
        // Reverse -> rot13 -> reverse -> Base64 decode
        val originalText = "https://stream.provider.com/master.m3u8"
        val b64 = Base64.getEncoder().encodeToString(originalText.toByteArray(Charsets.UTF_8))
        val d = b64.reversed()
        val c = d.map { ch ->
            when {
                (ch in 'a'..'m') || (ch in 'A'..'M') -> (ch.code + 13).toChar()
                (ch in 'n'..'z') || (ch in 'N'..'Z') -> (ch.code - 13).toChar()
                else -> ch
            }
        }.joinToString("")
        val encrypted = c.reversed()

        val decrypted = VidsrcExtractor.ihWrImMIGL(encrypted)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testVidsrcXTyCipher() {
        // Reversed, even index filter, Base64 decode
        val originalText = "https://example.com/hls.m3u8"
        val b64 = Base64.getEncoder().encodeToString(originalText.toByteArray(Charsets.UTF_8))
        // Inject dummy characters at odd indices
        val padded = StringBuilder()
        for (ch in b64) {
            padded.append(ch)
            padded.append('X')
        }
        val encrypted = padded.toString().reversed()

        val decrypted = VidsrcExtractor.xTyBxQyGTA(encrypted)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testVidnestExtractSubtitles() {
        val sampleScript = """
            jwplayer("vplayer").setup({
                sources: [{file: "https://vidnest.io/stream/master.m3u8"}],
                tracks: [
                    {kind: "captions", file: "https://vidnest.io/subs/en.vtt", label: "English", default: true},
                    {kind: "captions", file: "https://vidnest.io/subs/pl.vtt", label: "Polish", default: false},
                    {kind: "thumbnails", file: "https://vidnest.io/thumbs/preview.vtt", label: "Preview"}
                ]
            });
        """.trimIndent()

        val subtitles = VidnestExtractor.extractSubtitles(sampleScript)
        assertEquals(2, subtitles.size)
        assertEquals("English", subtitles[0].label)
        assertEquals("https://vidnest.io/subs/en.vtt", subtitles[0].url)
        assertEquals("Polish", subtitles[1].label)
        assertEquals("https://vidnest.io/subs/pl.vtt", subtitles[1].url)
    }

    @Test
    fun testVideasyParseUrlMovie() {
        val url = "https://player.videasy.net/movie/550"
        val params = VideasyExtractor.parseVideasyUrl(url)
        assertNotNull(params)
        assertEquals("550", params?.tmdbId)
        assertEquals("movie", params?.mediaType)
        assertEquals(VideasyExtractor.VideasyServer.YORU, params?.server)
        assertEquals("cdn", params?.server?.endpoint)
    }

    @Test
    fun testVideasyParseUrlWithSubServer() {
        val neonUrl = "https://player.videasy.net/movie/550?server=neon"
        val neonParams = VideasyExtractor.parseVideasyUrl(neonUrl)
        assertNotNull(neonParams)
        assertEquals(VideasyExtractor.VideasyServer.NEON, neonParams?.server)
        assertEquals("vsrc", neonParams?.server?.endpoint)
        assertEquals("Videasy (Neon)", neonParams?.server?.serverName)

        val breachUrl = "https://player.videasy.net/tv/1399/1/1?server=breach"
        val breachParams = VideasyExtractor.parseVideasyUrl(breachUrl)
        assertNotNull(breachParams)
        assertEquals(VideasyExtractor.VideasyServer.BREACH, breachParams?.server)
        assertEquals("m4uhd", breachParams?.server?.endpoint)

        val cypherUrl = "https://player.videasy.net/movie/550?server=cypher"
        val cypherParams = VideasyExtractor.parseVideasyUrl(cypherUrl)
        assertNotNull(cypherParams)
        assertEquals(VideasyExtractor.VideasyServer.CYPHER, cypherParams?.server)
        assertEquals("downloader2", cypherParams?.server?.endpoint)
    }

    @Test
    fun testVideasyParseUrlTv() {
        val url = "https://player.videasy.net/tv/1399/2/5"
        val params = VideasyExtractor.parseVideasyUrl(url)
        assertNotNull(params)
        assertEquals("1399", params?.tmdbId)
        assertEquals("tv", params?.mediaType)
        assertEquals(2, params?.season)
        assertEquals(5, params?.episode)
    }

    @Test
    fun testVideasyParseApiUrl() {
        val url =
            "https://api.speedracelight.com/cdn/sources-with-title?title=Game+of+Thrones&mediaType=tv&tmdbId=1399&seasonId=1&episodeId=2"
        val params = VideasyExtractor.parseVideasyUrl(url)
        assertNotNull(params)
        assertEquals("1399", params?.tmdbId)
        assertEquals("tv", params?.mediaType)
        assertEquals(1, params?.season)
        assertEquals(2, params?.episode)
    }

    @Test
    fun testTmdbClientGeneratesMovieEmbeds() = kotlinx.coroutines.runBlocking {
        val fakeInterceptor = okhttp3.Interceptor { chain ->
            val responseString = """{"page":1,"results":[{"id":508442,"title":"Soul"}]}"""
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseString.toResponseBody(null))
                .build()
        }
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor(fakeInterceptor)
            .build()
        val tmdbClient = com.pointlessapps.filman.data.scraper.TmdbClient(mockClient)
        val embeds = tmdbClient.getEmbeds(title = "Co w duszy gra", year = 2020)

        assertEquals(9, embeds.size)
        assertEquals("https://vsembed.ru/embed/movie?tmdb=508442", embeds[0].url)
        assertEquals("VidSrc", embeds[0].serverName)
        assertEquals("https://vidcore.io/movie/508442?autoPlay=true", embeds[1].url)
        assertEquals("VidCore", embeds[1].serverName)
        assertEquals("https://vidfast.vc/movie/508442?autoPlay=true", embeds[2].url)
        assertEquals("VidFast", embeds[2].serverName)
        assertEquals("https://vidnest.fun/movie/508442", embeds[3].url)
        assertEquals("VidNest", embeds[3].serverName)
        assertEquals("https://player.videasy.to/movie/508442?server=yoru", embeds[4].url)
        assertEquals("Videasy (Yoru)", embeds[4].serverName)
        assertEquals("https://player.videasy.to/movie/508442?server=breach", embeds[5].url)
        assertEquals("Videasy (Breach)", embeds[5].serverName)
        assertEquals("https://player.videasy.to/movie/508442?server=neon", embeds[6].url)
        assertEquals("Videasy (Neon)", embeds[6].serverName)
        assertEquals("https://player.videasy.to/movie/508442?server=cypher", embeds[7].url)
        assertEquals("Videasy (Cypher)", embeds[7].serverName)
        assertEquals("https://player.videasy.to/movie/508442?server=vyse", embeds[8].url)
        assertEquals("Videasy (Vyse)", embeds[8].serverName)
    }

    @Test
    fun testTmdbClientGeneratesTvEmbeds() = kotlinx.coroutines.runBlocking {
        val fakeInterceptor = okhttp3.Interceptor { chain ->
            val responseString = """{"page":1,"results":[{"id":1399,"name":"Game of Thrones"}]}"""
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseString.toResponseBody(null))
                .build()
        }
        val mockClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor(fakeInterceptor)
            .build()
        val tmdbClient = com.pointlessapps.filman.data.scraper.TmdbClient(mockClient)
        val embeds =
            tmdbClient.getEmbeds(title = "Gra o tron", year = 2011, season = 1, episode = 1)

        assertEquals(9, embeds.size)
        assertEquals("https://vsembed.ru/embed/tv?tmdb=1399&season=1&episode=1", embeds[0].url)
        assertEquals("VidSrc", embeds[0].serverName)
        assertEquals("https://vidcore.io/tv/1399/1/1?autoPlay=true", embeds[1].url)
        assertEquals("VidCore", embeds[1].serverName)
        assertEquals("https://vidfast.vc/tv/1399/1/1?autoPlay=true", embeds[2].url)
        assertEquals("VidFast", embeds[2].serverName)
        assertEquals("https://vidnest.fun/tv/1399/1/1", embeds[3].url)
        assertEquals("VidNest", embeds[3].serverName)
        assertEquals("https://player.videasy.to/tv/1399/1/1?server=yoru", embeds[4].url)
        assertEquals("Videasy (Yoru)", embeds[4].serverName)
        assertEquals("https://player.videasy.to/tv/1399/1/1?server=breach", embeds[5].url)
        assertEquals("Videasy (Breach)", embeds[5].serverName)
        assertEquals("https://player.videasy.to/tv/1399/1/1?server=neon", embeds[6].url)
        assertEquals("Videasy (Neon)", embeds[6].serverName)
        assertEquals("https://player.videasy.to/tv/1399/1/1?server=cypher", embeds[7].url)
        assertEquals("Videasy (Cypher)", embeds[7].serverName)
        assertEquals("https://player.videasy.to/tv/1399/1/1?server=vyse", embeds[8].url)
        assertEquals("Videasy (Vyse)", embeds[8].serverName)
    }

    @Test
    fun testVideasyDecrypt() {
        val enc = "KZoSeid0i0O49d0swzkvH53xSMG-kEbrOv3VTlP2WFB-BmgYXfdLL22Z6K8aHiOZsX6-WoUPd2ZkZL_8xaQPFyRnXxAQk8bpzLYmO-OhlrCg"
        val seed = "test-stream-seed-xyz"
        val mediaId = "508442"
        val expected = """{"sources":[{"url":"https://stream.example.com/master.m3u8"}],"subtitles":[]}"""
        val decrypted = VideasyExtractor.decrypt(enc, seed, mediaId)
        assertEquals(expected, decrypted)
    }
}
