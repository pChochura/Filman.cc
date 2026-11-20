package com.pointlessapps.filman.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

interface VideoHasher {
    suspend fun computeHash(url: String, headers: Map<String, String> = emptyMap()): String?
}

data class VideoProbeResult(
    val movieHash: String?,
    val fileSize: Long?,
    val finalUrl: String?,
    val contentDisposition: String?,
)

object OpenSubtitlesHasher : VideoHasher {
    const val CHUNK_SIZE = 65536L // 64 KB

    private val defaultClient by lazy {
        NetworkClient.okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun computeHash(
        url: String,
        headers: Map<String, String>,
    ): String? = withContext(Dispatchers.IO) {
        probeVideo(url, headers, defaultClient)?.movieHash
    }

    suspend fun probeVideo(
        url: String,
        headers: Map<String, String> = emptyMap(),
        client: OkHttpClient = defaultClient,
    ): VideoProbeResult? = withContext(Dispatchers.IO) {
        try {
            if (url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".mpd", ignoreCase = true)
            ) {
                return@withContext null
            }

            // 1. Issue a Range request for bytes 0..CHUNK_SIZE-1 directly.
            // This avoids a separate HEAD request (which many CDNs reject) and retrieves
            // the head chunk, Content-Range, final redirect URL, and Content-Disposition all in 1 RTT.
            val headChunkReq = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${CHUNK_SIZE - 1}")
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            var totalLength: Long = -1L
            var finalUrl: String? = null
            var contentDisposition: String? = null
            var headBytes: ByteArray? = null

            client.newCall(headChunkReq).execute().use { response ->
                if (response.code != 206) return@withContext null
                finalUrl = response.request.url.toString()
                contentDisposition = response.header("Content-Disposition")
                val contentRange = response.header("Content-Range")
                if (contentRange != null) {
                    val slashIdx = contentRange.lastIndexOf('/')
                    if (slashIdx != -1) {
                        totalLength = contentRange.substring(slashIdx + 1).trim().toLongOrNull() ?: -1L
                    }
                }
                headBytes = response.body.bytes()
            }

            val validHead = headBytes ?: return@withContext null
            if (validHead.size.toLong() < CHUNK_SIZE || totalLength <= CHUNK_SIZE * 2) {
                return@withContext VideoProbeResult(
                    movieHash = null,
                    fileSize = if (totalLength > 0) totalLength else null,
                    finalUrl = finalUrl,
                    contentDisposition = contentDisposition,
                )
            }

            // 2. Fetch the tail 64KB chunk
            val tailChunkReq = Request.Builder()
                .url(finalUrl ?: url)
                .header("Range", "bytes=${totalLength - CHUNK_SIZE}-${totalLength - 1}")
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

            val tailBytes = client.newCall(tailChunkReq).execute().use { response ->
                if (response.code != 206) null else response.body.bytes()
            } ?: return@withContext VideoProbeResult(
                movieHash = null,
                fileSize = totalLength,
                finalUrl = finalUrl,
                contentDisposition = contentDisposition,
            )

            if (tailBytes.size.toLong() < CHUNK_SIZE) {
                return@withContext VideoProbeResult(
                    movieHash = null,
                    fileSize = totalLength,
                    finalUrl = finalUrl,
                    contentDisposition = contentDisposition,
                )
            }

            val hash = computeHashFromChunks(validHead, tailBytes, totalLength)
            VideoProbeResult(
                movieHash = hash,
                fileSize = totalLength,
                finalUrl = finalUrl,
                contentDisposition = contentDisposition,
            )
        } catch (_: Exception) {
            null
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
