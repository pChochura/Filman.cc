package com.pointlessapps.filman.lite.download

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

internal data class HlsVariant(val url: String, val bandwidth: Long)

internal data class HlsMediaPlaylist(
    val segments: List<String>,
    val initSegment: String?,
    val encrypted: Boolean,
)

internal object HlsPlaylist {
    private val bandwidthRegex = Regex("(?<![A-Z-])BANDWIDTH=(\\d+)")
    private val uriRegex = Regex("URI=\"([^\"]+)\"")

    fun isHls(url: String) = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    fun resolve(base: String, reference: String): String =
        URLBuilder(Url(base)).takeFrom(reference).buildString()

    /** Returns the variants of a master playlist, or an empty list for a media playlist. */
    fun parseVariants(base: String, text: String): List<HlsVariant> {
        val lines = text.lines().map { it.trim() }
        val variants = mutableListOf<HlsVariant>()
        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return@forEachIndexed
            val bandwidth = bandwidthRegex.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            variants += HlsVariant(resolve(base, uri), bandwidth)
        }
        return variants
    }

    fun parseMedia(base: String, text: String): HlsMediaPlaylist {
        val segments = mutableListOf<String>()
        var init: String? = null
        var encrypted = false
        text.lines().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("#EXT-X-KEY") && !line.contains("METHOD=NONE") -> encrypted = true
                line.startsWith("#EXT-X-MAP") ->
                    uriRegex.find(line)?.groupValues?.get(1)?.let { init = resolve(base, it) }
                line.isNotEmpty() && !line.startsWith("#") -> segments += resolve(base, line)
            }
        }
        return HlsMediaPlaylist(segments, init, encrypted)
    }
}
