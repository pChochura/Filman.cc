package com.pointlessapps.filman.player.lektor.engine

import com.pointlessapps.filman.player.lektor.model.LektorVoice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
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

class EdgeTtsClient(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun synthesize(text: String, voice: LektorVoice = LektorVoice.EDGE): ByteArray {
        require(text.isNotBlank()) { "Subtitle text must not be blank" }
        require(voice.isOnline) { "Voice must be an Edge neural voice" }
        val result = CompletableDeferred<ByteArray>()
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val now = System.currentTimeMillis()
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("TrustedClientToken", TRUSTED_CLIENT_TOKEN)
            .addQueryParameter("ConnectionId", UUID.randomUUID().toString().replace("-", ""))
            .addQueryParameter("Sec-MS-GEC", gecToken(now / 1000))
            .addQueryParameter("Sec-MS-GEC-Version", GEC_VERSION)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
        val audio = ByteArrayOutputStream()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val timestamp = timestamp(System.currentTimeMillis())
                if (!webSocket.send(configMessage(timestamp)) ||
                    !webSocket.send(ssmlMessage(requestId, timestamp, text, voice))
                ) {
                    result.completeExceptionally(IOException("Could not send speech request"))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val path = headers(text.substringBefore("\r\n\r\n"))["path"]
                if (path == "turn.end") {
                    if (audio.size() == 0) result.completeExceptionally(IOException("Speech response contained no audio"))
                    else result.complete(audio.toByteArray())
                } else if (path != "turn.start" && path != "response" && path != "audio.metadata") {
                    result.completeExceptionally(IOException("Unexpected speech response path: $path"))
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
                result.completeExceptionally(IOException("Speech connection failed (HTTP ${response?.code ?: "unavailable"})", t))
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

    companion object {
        private const val ENDPOINT = "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val GEC_VERSION = "2-143.0.3650.75"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val MAX_AUDIO_BYTES = 2 * 1024 * 1024

        internal fun gecToken(unixSeconds: Long): String {
            return EdgeGecToken.generate(unixSeconds, TRUSTED_CLIENT_TOKEN)
        }

        internal fun timestamp(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(millis))

        internal fun configMessage(timestamp: String): String =
            "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""

        internal fun ssmlMessage(
            requestId: String,
            timestamp: String,
            text: String,
            voice: LektorVoice = LektorVoice.EDGE,
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
                "<voice name=\"$voiceName\"><prosody rate=\"+15%\" pitch=\"+0Hz\">$safeText</prosody></voice></speak>"
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
            ) throw IOException("Unexpected speech audio content type")
            return payload
        }

        private fun headers(header: String): Map<String, String> =
            header.split("\r\n").mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else line.substring(0, separator).trim().lowercase(Locale.US) to line.substring(separator + 1).trim()
            }.toMap()
    }
}
