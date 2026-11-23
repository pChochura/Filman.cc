package com.pointlessapps.filman.data.api

import java.net.URLDecoder

object VideoSignatureExtractor {
    private val GENERIC_NAMES = setOf(
        "index", "master", "playlist", "video", "stream", "play", "hls", "output",
        "manifest", "chunk", "segment", "track", "default", "source", "download",
        "embed", "player", "movie", "tv", "media", "file"
    )

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "webm", "m4v", "mov", "ts", "m3u8", "mpd"
    )

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "in", "of", "to", "for", "with",
        "pl", "pol", "polish", "polski", "napisy", "srt", "sub", "vtt",
        "lektor", "dubbing", "audio", "video", "multisubs", "multi", "264", "265"
    )

    fun tokenize(name: String): Set<String> {
        return name.split(Regex("[^a-zA-Z0-9]+"))
            .map { it.lowercase() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .toSet()
    }

    fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private val RELEASE_TAG_REGEX = Regex(
        "(1080p|720p|2160p|4k|480p|web-?dl|web-?rip|bluray|bdrip|brrip|hdtv|dvdrip|x264|x265|hevc|h264|h265|ddp5\\.1|aac|yts|flux|cmrg|evo|sparks|rarbg)",
        RegexOption.IGNORE_CASE
    )

    fun extract(
        videoUrl: String?,
        headers: Map<String, String> = emptyMap(),
        serverName: String? = null,
        quality: String? = null,
        filename: String? = null,
    ): String? {
        if (!filename.isNullOrBlank()) {
            val clean = cleanFilename(filename)
            if (clean.isNotBlank() && !isGenericName(clean) && clean.length >= 8) {
                return clean
            }
        }

        if (videoUrl.isNullOrBlank()) return null

        // 0. Unwrap proxy URL if present (e.g. /m3u8-proxy?url=..., /ts-proxy?url=...)
        val targetUrl = unwrapProxyUrl(videoUrl)

        // 1. Check Content-Disposition header
        val contentDisposition = headers.entries.firstOrNull {
            it.key.equals("Content-Disposition", ignoreCase = true)
        }?.value
        if (!contentDisposition.isNullOrBlank()) {
            val filenameFromHeader = extractFilenameFromContentDisposition(contentDisposition)
            if (!filenameFromHeader.isNullOrBlank() && !isGenericName(filenameFromHeader)) {
                return cleanFilename(filenameFromHeader)
            }
        }

        // 2. Check URL query parameters (e.g. ?title=..., ?file=..., ?filename=..., ?name=..., ?url=...)
        val queryFilename = extractFilenameFromQueryParams(targetUrl)
        if (!queryFilename.isNullOrBlank() && !isGenericName(queryFilename)) {
            return cleanFilename(queryFilename)
        }

        // 3. Check URL path segments (search from end to beginning for release-like names)
        val path = if (targetUrl.contains("://")) {
            targetUrl.substringAfter("://").substringAfter('/', "").substringBefore('?')
        } else {
            targetUrl.substringBefore('?')
        }
        val segments = path.split('/').filter { it.isNotBlank() }
        for (segment in segments.reversed()) {
            val decoded = runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
            val cleaned = cleanFilename(decoded)
            if (cleaned.isNotBlank() && !isGenericName(cleaned)) {
                if (RELEASE_TAG_REGEX.containsMatchIn(cleaned) && cleaned.length >= 8) {
                    return cleaned
                }
            }
        }

        // 4. Fallback: pick the last non-generic segment with sufficient length
        for (segment in segments.reversed()) {
            val decoded = runCatching { URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)
            val cleaned = cleanFilename(decoded)
            if (cleaned.isNotBlank() && !isGenericName(cleaned) && cleaned.length >= 8) {
                return cleaned
            }
        }

        // 5. Fallback: construct from serverName and quality if available
        if (!serverName.isNullOrBlank()) {
            if (serverName.contains("\n")) {
                for (line in serverName.lines()) {
                    val cleanLine = cleanFilename(line)
                    if (cleanLine.isNotBlank() && !isGenericName(cleanLine) && cleanLine.length >= 8) {
                        if (RELEASE_TAG_REGEX.containsMatchIn(cleanLine)) {
                            return cleanLine
                        }
                    }
                }
            }
            if (!quality.isNullOrBlank() && RELEASE_TAG_REGEX.containsMatchIn(serverName)) {
                return "$serverName.$quality"
            }
            if (RELEASE_TAG_REGEX.containsMatchIn(serverName)) {
                return serverName
            }
        }

        return null
    }

    private fun unwrapProxyUrl(url: String): String {
        if (url.contains("/m3u8-proxy") || url.contains("/ts-proxy")) {
            val nestedUrl = extractQueryParam(url, "url")
            if (!nestedUrl.isNullOrBlank()) {
                val decoded = runCatching { URLDecoder.decode(nestedUrl, "UTF-8") }.getOrDefault(nestedUrl)
                return decoded
            }
        }
        return url
    }

    private fun extractQueryParam(url: String, keyToFind: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return null
        for (param in query.split('&')) {
            val key = param.substringBefore('=').lowercase()
            if (key == keyToFind.lowercase()) {
                return param.substringAfter('=', "")
            }
        }
        return null
    }

    private fun extractFilenameFromContentDisposition(header: String): String? {
        val match = Regex("""filename\*?=['"]?(?:UTF-\d['"]*)?([^'";\n]+)['"]?""", RegexOption.IGNORE_CASE)
            .find(header)
        return match?.groupValues?.get(1)?.trim('"', '\'', ' ')
    }

    private fun extractFilenameFromQueryParams(url: String): String? {
        val query = url.substringAfter('?', "")
        if (query.isBlank()) return null
        val params = query.split('&')
        val targetKeys = setOf("title", "filename", "file", "name", "release")
        for (param in params) {
            val key = param.substringBefore('=').lowercase()
            val value = param.substringAfter('=', "")
            if (key in targetKeys && value.isNotBlank()) {
                val decoded = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
                if (decoded.length >= 6) {
                    return decoded
                }
            }
        }
        return null
    }

    fun cleanFilename(name: String): String {
        var result = name.substringBefore('?').trim('"', '\'', ' ')
        for (ext in VIDEO_EXTENSIONS) {
            if (result.endsWith(".$ext", ignoreCase = true)) {
                result = result.substring(0, result.length - ext.length - 1)
                break
            }
        }
        return result.trim('"', '\'', ' ')
    }

    private val EPISODE_REGEX = Regex(
        """[sS](\d{1,2})[eE](\d{1,2})|(\d{1,2})x(\d{1,2})"""
    )

    private val YEAR_REGEX = Regex("""\b(19\d\d|20\d\d)\b""")

    fun extractSeasonEpisode(text: String): Pair<Int, Int>? {
        val match = EPISODE_REGEX.find(text) ?: return null
        val s = (match.groupValues[1].ifEmpty { match.groupValues[3] }).toIntOrNull() ?: return null
        val e = (match.groupValues[2].ifEmpty { match.groupValues[4] }).toIntOrNull() ?: return null
        return s to e
    }

    fun extractYear(text: String): Int? {
        return YEAR_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun isEpisodeMismatched(text: String, expectedSeason: Int?, expectedEpisode: Int?): Boolean {
        if (expectedSeason == null && expectedEpisode == null) return false
        val (s, e) = extractSeasonEpisode(text) ?: return false
        if (expectedSeason != null && s != expectedSeason) return true
        if (expectedEpisode != null && e != expectedEpisode) return true
        return false
    }

    fun computeConfidence(text: String, expectedTitle: String?, expectedYear: Int?): Int {
        if (text.isBlank()) return 0
        val year = extractYear(text)
        if (expectedYear != null && year != null && kotlin.math.abs(expectedYear - year) > 1) {
            return -50
        }
        if (!expectedTitle.isNullOrBlank()) {
            val candTokens = tokenize(text)
            val targetTokens = tokenize(expectedTitle)
            if (targetTokens.isNotEmpty() && candTokens.isNotEmpty()) {
                val sim = jaccardSimilarity(candTokens, targetTokens)
                if (sim >= 0.35) return 10
                if (sim < 0.1 && candTokens.size >= 2) return -20
            }
        }
        return 0
    }

    private fun isGenericName(name: String): Boolean {
        val base = cleanFilename(name).lowercase()
        return base in GENERIC_NAMES ||
                base.matches(Regex("^[0-9a-f]{16,}$")) || // hexadecimal hashes
                base.matches(Regex("^[0-9a-f\\-]{32,}$")) || // UUIDs
                base.matches(Regex("^[0-9]+$")) // purely numeric
    }
}
