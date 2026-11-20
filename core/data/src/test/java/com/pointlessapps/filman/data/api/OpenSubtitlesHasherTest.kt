package com.pointlessapps.filman.data.api

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpenSubtitlesHasherTest {

    @Test
    fun `computeHashFromChunks correctly computes 64-bit checksum`() {
        val head = ByteArray(OpenSubtitlesHasher.CHUNK_SIZE.toInt())
        val tail = ByteArray(OpenSubtitlesHasher.CHUNK_SIZE.toInt())

        // Put long value 1 at start of head (little endian)
        ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().put(1L)
        // Put long value 2 at start of tail (little endian)
        ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().put(2L)

        val fileSize = 100_000_000L
        // Expected hash: 100_000_000 + 1 + 2 = 100000003 -> in hex "%016x"
        val expected = String.format("%016x", 100000003L)

        val hash = OpenSubtitlesHasher.computeHashFromChunks(head, tail, fileSize)
        assertEquals(expected, hash)
    }

    @Test
    fun `computeHash skips m3u8 streams`() = runBlocking {
        val hash = OpenSubtitlesHasher.computeHash("https://cdn.example.com/stream/master.m3u8")
        assertNull(hash)
    }

    @Test
    fun `probeVideo executes Range GET requests and extracts headers`() = runBlocking {
        MockWebServer().use { server ->
            val chunkSize = OpenSubtitlesHasher.CHUNK_SIZE.toInt()
            val totalSize = 10_000_000L

            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-${chunkSize - 1}/$totalSize")
                    .setHeader("Content-Disposition", "attachment; filename=\"Test.Movie.2024.1080p.mkv\"")
                    .setBody(okio.Buffer().write(ByteArray(chunkSize)))
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes ${totalSize - chunkSize}-${totalSize - 1}/$totalSize")
                    .setBody(okio.Buffer().write(ByteArray(chunkSize)))
            )

            val client = OkHttpClient()
            val url = server.url("/video.mp4").toString()
            val result = OpenSubtitlesHasher.probeVideo(url, client = client)

            assertNotNull(result)
            assertEquals(String.format("%016x", totalSize), result?.movieHash)
            assertEquals(totalSize, result?.fileSize)
            assertEquals("attachment; filename=\"Test.Movie.2024.1080p.mkv\"", result?.contentDisposition)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `computeHash returns null when server does not support range requests`() = runBlocking {
        MockWebServer().use { server ->
            val totalSize = 10_000_000L

            // Server responds 200 instead of 206 for range
            server.enqueue(MockResponse().setResponseCode(200).setBody("Full file"))

            val client = OkHttpClient()
            val url = server.url("/video.mp4").toString()
            val result = OpenSubtitlesHasher.probeVideo(url, client = client)

            assertNull(result)
        }
    }
}
