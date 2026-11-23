package com.pointlessapps.filman.data.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StreamApiLinkSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `wake up runs in background and requests health under base path`() = runBlocking {
        val server = MockWebServer()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                if (!release.await(5, TimeUnit.SECONDS)) return MockResponse().setResponseCode(500)
                return MockResponse().setResponseCode(200)
            }
        }
        server.start()
        try {
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/prefix/").toString(), temporaryFolder.newFolder())
            val job = source.wakeUp()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(job.isActive)
            assertEquals("/prefix/api/health", server.takeRequest().path)
            release.countDown()
            withTimeout(5_000) { job.join() }
        } finally {
            release.countDown()
            server.shutdown()
        }
    }

    @Test
    fun `failed wake up does not prevent subsequent stream requests`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(streamResponse("""{"success":true,"streams":[{"url":"https://example.test/a"}]}"""))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString(), temporaryFolder.newFolder())
            withTimeout(5_000) { source.wakeUp().join() }
            val key = source.linkKey(1)
            source.startExtraction(1)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            assertEquals("READY", source.getLinks(key)?.status)
            assertEquals("/api/health", server.takeRequest().path)
            assertEquals("/api/streams/movie/1?stream=1", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `wake up rejects invalid base URLs without making a request`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val base = server.url("/").toString() + "?unexpected=1"
            val source = StreamApiLinkSource(OkHttpClient(), base, temporaryFolder.newFolder())
            withTimeout(5_000) { source.wakeUp().join() }
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `episode response preserves stream metadata`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(streamResponse(
                """{"success":true,"streams":[{"url":"https://example.test/a.m3u8","name":"Server","quality":"1080p","headers":{"Referer":"https://example.test/"},"subtitles":[{"url":"https://example.test/en.vtt","lang":"en","label":"English"}]}]}""",
            ))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/prefix/").toString(), temporaryFolder.newFolder())
            source.startExtraction(123, isTvShow = true, season = 1, episode = 2)
            val key = source.linkKey(123, 1, 2)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            val response = source.getLinks(key)!!
            assertEquals("READY", response.status)
            assertEquals("Server", response.links.single().serverName)
            assertEquals("1080p", response.links.single().quality)
            assertEquals("https://example.test/", response.links.single().headers["Referer"])
            assertEquals("en", response.links.single().subtitles.single().language)
            assertEquals("English", response.links.single().subtitles.single().label)
            val request = server.takeRequest()
            assertEquals("/prefix/api/streams/series/123?season=1&episode=2&stream=1", request.path)
            assertEquals("text/event-stream", request.getHeader("Accept"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `empty configuration reports an error without requesting a server`() = runBlocking {
        val source = StreamApiLinkSource(OkHttpClient(), "", temporaryFolder.newFolder())
        source.startExtraction(123)
        withTimeout(5_000) {
            while (source.getLinks(source.linkKey(123))?.status in listOf("PENDING", "PARTIAL")) delay(10)
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
            server.enqueue(streamResponse(
                """{"success":true,"streams":[{"url":"https://example.test/movie.mp4","provider":"test"}]}""",
            ))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString(), temporaryFolder.newFolder())
            val key = source.linkKey(42)
            source.startExtraction(42)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            assertEquals("ERROR", source.getLinks(key)?.status)
            assertEquals("/api/streams/movie/42?stream=1", server.takeRequest().path)
            source.startExtraction(42, force = true)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            assertEquals("READY", source.getLinks(key)?.status)
            assertEquals("test", source.getLinks(key)?.links?.single()?.serverName)
            assertEquals("/api/streams/movie/42?stream=1", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `malformed responses and empty results are errors`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(streamResponse("""{"success":true}"""))
            server.enqueue(streamResponse("""{"success":true,"streams":[]}"""))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString(), temporaryFolder.newFolder())
            val key = source.linkKey(12)
            source.startExtraction(12)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            assertEquals("ERROR", source.getLinks(key)?.status)
            source.startExtraction(12, force = true)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            assertEquals("ERROR", source.getLinks(key)?.status)
            assertEquals("Stream API returned no streams", source.getLinks(key)?.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `successful responses survive recreation and expire without extending their lifetime`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val directory = temporaryFolder.newFolder()
            val base = server.url("/").toString()
            var time = 1_000_000L
            fun source() = StreamApiLinkSource(OkHttpClient(), base, directory) { time }
            val first = source()
            server.enqueue(streamResponse(
                """{"success":true,"streams":[{"url":"https://example.test/a","name":"A","headers":{"Referer":"https://example.test"},"subtitles":[{"url":"https://example.test/sub.vtt","lang":"en"}]},{"url":"https://example.test/a","name":"B"}]}""",
            ))
            first.startExtraction(1)
            val key = first.linkKey(1)
            withTimeout(5_000) { while (first.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10) }
            assertEquals(2, first.getLinks(key)?.links?.size)

            time += 299_000
            val second = source()
            second.startExtraction(1)
            withTimeout(5_000) { while (second.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10) }
            val links = second.getLinks(key)!!.links
            assertEquals(2, links.size)
            assertEquals(listOf("A", "B"), links.map { it.serverName })
            assertEquals("https://example.test", links.first().headers["Referer"])
            assertEquals("en", links.first().subtitles.single().language)
            assertEquals(1, server.requestCount)

            time += 2_000
            server.enqueue(streamResponse("""{"success":true,"streams":[{"url":"https://example.test/new"}]}"""))
            second.startExtraction(1)
            withTimeout(5_000) { while (second.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10) }
            assertEquals("https://example.test/new", second.getLinks(key)?.links?.single()?.url)
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cache is isolated by server and clear removes persisted results`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val directory = temporaryFolder.newFolder()
            val base = server.url("/").toString()
            val first = StreamApiLinkSource(OkHttpClient(), base, directory)
            val other = StreamApiLinkSource(OkHttpClient(), server.url("/other/").toString(), directory)
            server.enqueue(streamResponse("""{"success":true,"streams":[{"url":"https://example.test/a"}]}"""))
            server.enqueue(streamResponse("""{"success":true,"streams":[{"url":"https://example.test/b"}]}"""))
            val key = first.linkKey(1)
            first.startExtraction(1)
            withTimeout(5_000) { while (first.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10) }
            other.startExtraction(1)
            withTimeout(5_000) { while (other.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10) }
            assertEquals("https://example.test/b", other.getLinks(key)?.links?.single()?.url)
            assertEquals(2, server.requestCount)

            first.clearCache()
            val recreated = StreamApiLinkSource(OkHttpClient(), base, directory)
            server.enqueue(streamResponse("""{"success":true,"streams":[{"url":"https://example.test/c"}]}"""))
            recreated.startExtraction(1)
            withTimeout(5_000) { while (recreated.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10) }
            assertEquals("https://example.test/c", recreated.getLinks(key)?.links?.single()?.url)
            assertEquals(3, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `clearing during a request prevents old results from restoring cache`() = runBlocking {
        val server = MockWebServer()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                entered.countDown()
                if (!release.await(5, TimeUnit.SECONDS)) return MockResponse().setResponseCode(500)
                return streamResponse("""{"success":true,"streams":[{"url":"https://example.test/old"}]}""")
            }
        }
        server.start()
        try {
            val directory = temporaryFolder.newFolder()
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString(), directory)
            source.startExtraction(1)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            source.clearCache()
            release.countDown()
            withTimeout(5_000) {
                while (server.requestCount != 1) delay(10)
            }
            delay(200)
            assertNull(source.getLinks(source.linkKey(1)))
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            release.countDown()
            server.shutdown()
        }
    }

    @Test
    fun `provider batches become available before completion and are cached only after completion`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val first = ": keepalive\r\nevent: start\r\ndata: {}\r\n\r\nevent: provider\r\ndata: " +
                """{"success":true,"streams":[{"url":"invalid"},{"url":"https://example.test/first","subtitles":[{"url":"invalid"}]}]}""" +
                "\r\n\r\n"
            val rest = "event: provider\ndata: " +
                """{"success":true,"streams":[{"url":"https://example.test/second"}]}""" +
                "\n\nevent: complete\ndata: {\"success\":true}\n\n"
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream")
                    .setBody(first + rest)
                    .throttleBody(first.toByteArray().size.toLong(), 2, TimeUnit.SECONDS),
            )
            val directory = temporaryFolder.newFolder()
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString(), directory)
            val key = source.linkKey(1)
            source.startExtraction(1)
            withTimeout(1_500) {
                while (source.getLinks(key)?.links.isNullOrEmpty()) delay(10)
            }
            assertEquals("PARTIAL", source.getLinks(key)?.status)
            assertEquals("https://example.test/first", source.getLinks(key)?.links?.single()?.url)
            assertTrue(source.getLinks(key)!!.links.single().subtitles.isEmpty())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            withTimeout(8_000) {
                while (source.getLinks(key)?.status == "PARTIAL") delay(10)
            }
            assertEquals("READY", source.getLinks(key)?.status)
            assertEquals(listOf("https://example.test/first", "https://example.test/second"), source.getLinks(key)?.links?.map { it.url })
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `interrupted and fatal streams retain playable partial results without caching them`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val provider = "event: provider\ndata: " +
                """{"success":true,"streams":[{"url":"https://example.test/first"}]}""" + "\n\n"
            server.enqueue(sseResponse(provider))
            server.enqueue(sseResponse(provider + "event: error\ndata: {\"error\":\"INTERNAL_ERROR\"}\n\n"))
            val directory = temporaryFolder.newFolder()
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString(), directory)
            val key = source.linkKey(1)
            repeat(2) { attempt ->
                source.startExtraction(1, force = attempt > 0)
                withTimeout(5_000) {
                    while (source.getLinks(key)?.status != "ERROR") delay(10)
                }
                assertEquals("https://example.test/first", source.getLinks(key)?.links?.single()?.url)
                assertTrue(source.getLinks(key)!!.message!!.contains(if (attempt == 0) "before completion" else "INTERNAL_ERROR"))
                assertTrue(directory.listFiles().orEmpty().isEmpty())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `failed provider and multiline data do not prevent later sources`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(sseResponse(
                "event: provider\ndata: {\"success\":false,\"streams\":[]}\n\n" +
                    "event: provider\ndata: {\"success\":true,\ndata: \"streams\":[{\"url\":\"https://example.test/a\"}]}\n\n" +
                    "event: complete\ndata: {\"success\":true}\n\n" +
                    "event: error\ndata: {\"error\":\"SHOULD_NOT_BE_READ\"}\n\n",
            ))
            val source = StreamApiLinkSource(OkHttpClient(), server.url("/").toString(), temporaryFolder.newFolder())
            val key = source.linkKey(1)
            source.startExtraction(1)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            assertEquals("READY", source.getLinks(key)?.status)
            assertEquals("https://example.test/a", source.getLinks(key)?.links?.single()?.url)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `stream extraction appends language parameter when supplied`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(streamResponse("""{"success":true,"streams":[{"url":"https://example.test/pl"}]}"""))
            val source = StreamApiLinkSource(
                OkHttpClient(),
                server.url("/").toString(),
                temporaryFolder.newFolder(),
                languageSupplier = { "pl" },
            )
            val key = source.linkKey(99)
            source.startExtraction(99)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            assertEquals("/api/streams/movie/99?stream=1&lang=pl", server.takeRequest().path)
            assertEquals("movie:99:pl", key)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `stream extraction extracts filename and version from single-line polish title`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(streamResponse("""{"success":true,"streams":[{"url":"https://example.test/stream","name":"Polish [CDA]","title":"[PL Dubbing] Epoka lodowcowa - 1080p","quality":"1080p","provider":"polish"}]}"""))
            val source = StreamApiLinkSource(
                OkHttpClient(),
                server.url("/").toString(),
                temporaryFolder.newFolder(),
            )
            val key = source.linkKey(101)
            source.startExtraction(101)
            withTimeout(5_000) {
                while (source.getLinks(key)?.status in listOf("PENDING", "PARTIAL")) delay(10)
            }
            val link = source.getLinks(key)?.links?.single()
            assertEquals("[PL Dubbing] Epoka lodowcowa - 1080p", link?.filename)
            assertEquals("Dubbing", link?.language)
            assertEquals("Polish [CDA]", link?.serverName)
        } finally {
            server.shutdown()
        }
    }

    private fun streamResponse(provider: String): MockResponse =
        sseResponse("event: provider\ndata: $provider\n\nevent: complete\ndata: {\"success\":true}\n\n")

    private fun sseResponse(body: String): MockResponse =
        MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)
}
