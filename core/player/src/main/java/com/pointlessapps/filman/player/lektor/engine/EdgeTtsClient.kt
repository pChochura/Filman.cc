package com.pointlessapps.filman.player.lektor.engine

import com.pointlessapps.filman.data.model.LektorEdgeVoiceOptions
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class EdgeSynthesisResult(
    val audio: ByteArray,
    val speechStartMs: Long? = null,
    val speechEndMs: Long? = null,
) {
    val speechDurationMs: Long?
        get() = if (speechStartMs != null && speechEndMs != null && speechEndMs > speechStartMs) {
            speechEndMs - speechStartMs
        } else {
            null
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EdgeSynthesisResult) return false
        return audio.contentEquals(other.audio) &&
            speechStartMs == other.speechStartMs &&
            speechEndMs == other.speechEndMs
    }

    override fun hashCode(): Int {
        var result = audio.contentHashCode()
        result = 31 * result + (speechStartMs?.hashCode() ?: 0)
        result = 31 * result + (speechEndMs?.hashCode() ?: 0)
        return result
    }
}

@Serializable
private data class EdgeMetadataEnvelope(
    @SerialName("Metadata") val metadata: List<EdgeMetadataItem> = emptyList(),
)

@Serializable
private data class EdgeMetadataItem(
    @SerialName("Type") val type: String? = null,
    @SerialName("Data") val data: EdgeMetadataData? = null,
)

@Serializable
private data class EdgeMetadataData(
    @SerialName("Offset") val offset: Long? = null,
    @SerialName("Duration") val duration: Long? = null,
)

class EdgeTtsClient(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun synthesize(
        text: String,
        voice: LektorVoice = LektorVoice.EDGE,
        rate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
        pitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
        volume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    ): ByteArray = synthesizeWithMetadata(text, voice, rate, pitch, volume).audio

    suspend fun synthesizeWithMetadata(
        text: String,
        voice: LektorVoice = LektorVoice.EDGE,
        rate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
        pitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
        volume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
    ): EdgeSynthesisResult {
        require(text.isNotBlank()) { "Subtitle text must not be blank" }
        require(voice.isOnline) { "Voice must be an Edge neural voice" }

        return try {
            executeSynthesize(text, voice, rate, pitch, volume)
        } catch (e: SpeechConnectionException) {
            if (e.code == 403) {
                if (e.serverDate == null) {
                    syncClockSkewFromServer()
                }
                executeSynthesize(text, voice, rate, pitch, volume)
            } else {
                throw e
            }
        }
    }

    private suspend fun syncClockSkewFromServer() {
        withContext(Dispatchers.IO) {
            try {
                val syncRequest = Request.Builder()
                    .url(VOICE_LIST_URL)
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(syncRequest).execute().use { resp ->
                    val serverDate = parseDateHeader(resp)
                    if (serverDate != null) {
                        val serverSeconds = serverDate.time / 1000L
                        val localSeconds = System.currentTimeMillis() / 1000L
                        clockSkewSeconds.set(serverSeconds - localSeconds)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun executeSynthesize(
        text: String,
        voice: LektorVoice,
        rate: String,
        pitch: String,
        volume: String,
    ): EdgeSynthesisResult {
        val result = CompletableDeferred<EdgeSynthesisResult>()
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val request = buildWebSocketRequest()
        val audio = ByteArrayOutputStream()
        var minSpeechStartMs: Long? = null
        var maxSpeechEndMs: Long? = null

        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val timestamp = timestamp(System.currentTimeMillis() + (clockSkewSeconds.get() * 1000L))
                if (!webSocket.send(configMessage(timestamp)) ||
                    !webSocket.send(ssmlMessage(requestId, timestamp, text, voice, rate, pitch, volume))
                ) {
                    result.completeExceptionally(IOException("Could not send speech request"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val path = headers(text.substringBefore("\r\n\r\n"))["path"]
                when (path) {
                    "audio.metadata" -> {
                        val body = text.substringAfter("\r\n\r\n", "")
                        parseWordBoundaryRangeMs(body)?.let { (startMs, endMs) ->
                            minSpeechStartMs = minSpeechStartMs?.let { minOf(it, startMs) } ?: startMs
                            maxSpeechEndMs = maxSpeechEndMs?.let { maxOf(it, endMs) } ?: endMs
                        }
                    }
                    "turn.end" -> {
                        if (audio.size() == 0) {
                            result.completeExceptionally(IOException("Speech response contained no audio"))
                        } else {
                            result.complete(
                                EdgeSynthesisResult(
                                    audio = audio.toByteArray(),
                                    speechStartMs = minSpeechStartMs,
                                    speechEndMs = maxSpeechEndMs,
                                ),
                            )
                        }
                    }
                    "turn.start", "response" -> Unit
                    else -> result.completeExceptionally(IOException("Unexpected speech response path: $path"))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val payload = parseAudioFrame(bytes.toByteArray())
                    if (audio.size() + payload.size > MAX_AUDIO_BYTES) {
                        throw IOException("Speech audio exceeds size limit")
                    }
                    audio.write(payload)
                } catch (e: IOException) {
                    result.completeExceptionally(e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val httpCode = response?.code
                val serverDate = parseDateHeader(response)
                if (httpCode == 403) {
                    if (serverDate != null) {
                        val serverSeconds = serverDate.time / 1000L
                        val localSeconds = System.currentTimeMillis() / 1000L
                        clockSkewSeconds.set(serverSeconds - localSeconds)
                    }
                    result.completeExceptionally(SpeechConnectionException(httpCode, serverDate, t))
                } else {
                    result.completeExceptionally(
                        IOException("Speech connection failed (HTTP ${httpCode ?: "unavailable"})", t),
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                result.completeExceptionally(IOException("Speech connection closed before completion ($code)"))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                result.completeExceptionally(IOException("Speech connection closed before completion ($code)"))
                webSocket.close(code, null)
            }
        })
        try {
            return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { result.await() }
                ?: throw IOException("Speech request timed out")
        } finally {
            socket.cancel()
        }
    }

    private fun buildWebSocketRequest(): Request {
        val skew = clockSkewSeconds.get()
        val nowMillis = System.currentTimeMillis() + (skew * 1000L)
        val nowSeconds = nowMillis / 1000L
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("TrustedClientToken", TRUSTED_CLIENT_TOKEN)
            .addQueryParameter("ConnectionId", UUID.randomUUID().toString().replace("-", ""))
            .addQueryParameter("Sec-MS-GEC", gecToken(nowSeconds))
            .addQueryParameter("Sec-MS-GEC-Version", GEC_VERSION)
            .build()
        val muid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.5")
            .header("Cookie", "muid=$muid;")
            .build()
    }

    internal class SpeechConnectionException(
        val code: Int?,
        val serverDate: Date?,
        cause: Throwable?,
    ) : IOException("Speech connection failed (HTTP ${code ?: "unavailable"})", cause)

    companion object {
        private const val ENDPOINT =
            "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val GEC_VERSION = "2-143.0.3650.75"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
        private const val VOICE_LIST_URL =
            "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list" +
                "?trustedclienttoken=$TRUSTED_CLIENT_TOKEN"
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val MAX_AUDIO_BYTES = 2 * 1024 * 1024
        private const val TICKS_PER_MS = 10_000L

        private val metadataJsonParser = Json { ignoreUnknownKeys = true }

        internal val clockSkewSeconds = AtomicLong(0L)

        internal fun gecToken(unixSeconds: Long): String =
            EdgeGecToken.generate(unixSeconds, TRUSTED_CLIENT_TOKEN)

        internal fun parseWordBoundaryRangeMs(metadataJson: String): Pair<Long, Long>? = runCatching {
            val envelope = metadataJsonParser.decodeFromString<EdgeMetadataEnvelope>(metadataJson)
            var minStartMs: Long? = null
            var maxEndMs: Long? = null
            for (item in envelope.metadata) {
                if (item.type != "WordBoundary" && item.type != "SentenceBoundary") continue
                val offsetTicks = item.data?.offset ?: continue
                val durationTicks = item.data.duration ?: continue
                if (offsetTicks < 0L || durationTicks <= 0L) continue
                val startMs = offsetTicks / TICKS_PER_MS
                val endMs = (offsetTicks + durationTicks) / TICKS_PER_MS
                minStartMs = minStartMs?.let { minOf(it, startMs) } ?: startMs
                maxEndMs = maxEndMs?.let { maxOf(it, endMs) } ?: endMs
            }
            if (minStartMs != null && maxEndMs != null && maxEndMs > minStartMs) {
                minStartMs to maxEndMs
            } else {
                null
            }
        }.getOrNull()

        internal fun parseDateHeader(response: Response?): Date? {
            if (response == null) return null
            return try {
                response.headers.getDate("Date")
            } catch (_: Exception) {
                null
            } ?: response.header("Date")?.let { dateStr ->
                try {
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT")
                    }.parse(dateStr)
                } catch (_: Exception) {
                    null
                }
            }
        }

        internal fun timestamp(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(millis))

        internal fun configMessage(timestamp: String): String =
            "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                """{"context":{"synthesis":{"audio":{"metadataoptions":""" +
                """{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"true"},""" +
                """"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""

        internal fun ssmlMessage(
            requestId: String,
            timestamp: String,
            text: String,
            voice: LektorVoice = LektorVoice.EDGE,
            rate: String = LektorEdgeVoiceOptions.DEFAULT_RATE,
            pitch: String = LektorEdgeVoiceOptions.DEFAULT_PITCH,
            volume: String = LektorEdgeVoiceOptions.DEFAULT_VOLUME,
        ): String {
            val voiceName = requireNotNull(voice.edgeVoiceName) { "Voice must be an Edge neural voice" }
            val safeText = buildString {
                text.codePoints().forEach { code ->
                    when (code) {
                        38 -> append("&amp;")
                        60 -> append("&lt;")
                        62 -> append("&gt;")
                        34 -> append("&quot;")
                        39 -> append("&apos;")
                        9, 10, 13 -> appendCodePoint(code)
                        in 0x20..0xD7FF, in 0xE000..0xFFFD, in 0x10000..0x10FFFF -> appendCodePoint(code)
                        else -> append(' ')
                    }
                }
            }
            return "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\n" +
                "X-Timestamp:$timestamp\r\nPath:ssml\r\n\r\n" +
                "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"pl-PL\">" +
                "<voice name=\"$voiceName\"><prosody rate=\"$rate\" pitch=\"$pitch\" volume=\"$volume\">" +
                "$safeText</prosody></voice></speak>"
        }

        internal fun parseAudioFrame(frame: ByteArray): ByteArray {
            if (frame.size < 2) throw IOException("Missing speech frame header length")
            val length = ByteBuffer.wrap(frame, 0, 2).short.toInt() and 0xffff
            if (length > frame.size - 2) throw IOException("Invalid speech frame header length")
            val fields = headers(frame.copyOfRange(2, 2 + length).toString(Charsets.UTF_8))
            if (fields["path"] != "audio") throw IOException("Unexpected binary speech frame")
            val payload = frame.copyOfRange(2 + length, frame.size)
            val contentType = fields["content-type"]
            if ((payload.isNotEmpty() && contentType != "audio/mpeg") ||
                (payload.isEmpty() && contentType != null && contentType != "audio/mpeg")
            ) {
                throw IOException("Unexpected speech audio content type")
            }
            return payload
        }

        private fun headers(header: String): Map<String, String> =
            header.split("\r\n").mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) {
                    null
                } else {
                    line.substring(0, separator).trim().lowercase(Locale.US) to line.substring(separator + 1).trim()
                }
            }.toMap()
    }
}
