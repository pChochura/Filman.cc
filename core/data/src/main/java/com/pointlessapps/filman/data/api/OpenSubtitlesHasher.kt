package com.pointlessapps.filman.data.api

import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

interface VideoHasher {
    fun computeHash(url: String, headers: Map<String, String> = emptyMap()): String?
}

object OpenSubtitlesHasher : VideoHasher {
    const val CHUNK_SIZE = 65536L // 64 KB

    private val defaultClient by lazy {
        NetworkClient.okHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    override fun computeHash(
        url: String,
        headers: Map<String, String>,
    ): String? = computeHash(url, headers, defaultClient)

    fun computeHash(
        url: String,
        headers: Map<String, String> = emptyMap(),
        client: OkHttpClient = defaultClient,
    ): String? {
        try {
            if (url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".mpd", ignoreCase = true)
            ) {
                return null
            }

            val headReq = Request.Builder()
                .url(url)
                .head()
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            var totalLength: Long = -1L
            client.newCall(headReq).execute().use { response ->
                if (response.isSuccessful) {
                    val cl = response.header("Content-Length")?.toLongOrNull()
                    if (cl != null && cl > CHUNK_SIZE * 2) {
                        totalLength = cl
                    }
                }
            }

            if (totalLength <= CHUNK_SIZE * 2) return null

            val headChunkReq = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${CHUNK_SIZE - 1}")
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            val headBytes = client.newCall(headChunkReq).execute().use { response ->
                if (response.code != 206) return null
                response.body.bytes()
            }
            if (headBytes.size.toLong() < CHUNK_SIZE) return null

            val tailChunkReq = Request.Builder()
                .url(url)
                .header("Range", "bytes=${totalLength - CHUNK_SIZE}-${totalLength - 1}")
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            val tailBytes = client.newCall(tailChunkReq).execute().use { response ->
                if (response.code != 206) return null
                response.body.bytes()
            }
            if (tailBytes.size.toLong() < CHUNK_SIZE) return null

            return computeHashFromChunks(headBytes, tailBytes, totalLength)
        } catch (_: Exception) {
            return null
        }
    }

    fun computeHashFromChunks(head: ByteArray, tail: ByteArray, fileSize: Long): String {
        var hash = fileSize

        val headBuf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        while (headBuf.hasRemaining()) {
            hash += headBuf.get()
        }

        val tailBuf = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
        while (tailBuf.hasRemaining()) {
            hash += tailBuf.get()
        }

        return String.format("%016x", hash)
    }
}
