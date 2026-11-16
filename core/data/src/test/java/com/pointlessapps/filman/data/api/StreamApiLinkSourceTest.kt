package com.pointlessapps.filman.data.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamApiLinkSourceTest {
    @Test
    fun `episode response preserves stream metadata`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(
                """{"success":true,"streams":[{"url":"https://example.test/a.m3u8","name":"Server","quality":"1080p","headers":{"Referer":"https://example.test/"},"subtitles":[{"url":"https://example.test/en.vtt","lang":"en","label":"English"}]}]}""",
            ))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/prefix/").toString())
            source.startExtraction(123, isTvShow = true, season = 1, episode = 2)
            val key = source.linkKey(123, 1, 2)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status == "PENDING") delay(10)
            }
            val response = source.getLinks(key)!!
            assertEquals("READY", response.status)
            assertEquals("Server", response.links.single().serverName)
            assertEquals("1080p", response.links.single().quality)
            assertEquals("https://example.test/", response.links.single().headers["Referer"])
            assertEquals("en", response.links.single().subtitles.single().language)
            assertEquals("English", response.links.single().subtitles.single().label)
            assertEquals("/prefix/api/streams/series/123?season=1&episode=2", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `empty configuration reports an error without requesting a server`() = runBlocking {
        val source = StreamApiLinkSource(OkHttpClient(), "")
        source.startExtraction(123)
        withTimeout(5_000) {
            while (source.getLinks(source.linkKey(123))?.status == "PENDING") delay(10)
        }
        val response = source.getLinks(source.linkKey(123))!!
        assertEquals("ERROR", response.status)
        assertTrue(response.message!!.contains("STREAM_API_BASE_URL"))
    }

    @Test
    fun `movie HTTP failures are reported and forced requests can retry`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(
                """{"success":true,"streams":[{"url":"https://example.test/movie.mp4","provider":"test"}]}""",
            ))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString())
            val key = source.linkKey(42)
            source.startExtraction(42)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status == "PENDING") delay(10)
            }
            assertEquals("ERROR", source.getLinks(key)?.status)
            assertEquals("/api/streams/movie/42", server.takeRequest().path)
            source.startExtraction(42, force = true)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status == "PENDING") delay(10)
            }
            assertEquals("READY", source.getLinks(key)?.status)
            assertEquals("test", source.getLinks(key)?.links?.single()?.serverName)
            assertEquals("/api/streams/movie/42", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `malformed responses and empty results are errors`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"success":true}"""))
            server.enqueue(MockResponse().setBody("""{"success":true,"streams":[]}"""))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString())
            val key = source.linkKey(12)
            source.startExtraction(12)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status == "PENDING") delay(10)
            }
            assertEquals("ERROR", source.getLinks(key)?.status)
            source.startExtraction(12, force = true)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status == "PENDING") delay(10)
            }
            assertEquals("ERROR", source.getLinks(key)?.status)
            assertEquals("Stream API returned no streams", source.getLinks(key)?.message)
        } finally {
            server.shutdown()
        }
    }
}
