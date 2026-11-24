package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SettingsConstants

object VideoQualityUtils {
    fun parseQualityResolution(quality: String): Int {
        val q = quality.lowercase().trim()
        if (q.contains("4k") || q.contains("2160")) return 2160
        if (q.contains("1440") || q.contains("2k")) return 1440
        if (q.contains("1080")) return 1080
        if (q.contains("720")) return 720
        if (q.contains("480")) return 480
        if (q.contains("360")) return 360
        if (q.contains("240")) return 240
        return Regex("""\d+""").find(q)?.value?.toIntOrNull() ?: 0
    }

    fun qualityRank(quality: String, version: String, preferred: String): Int {
        val pref = preferred.lowercase().trim()
        val qLower = quality.lowercase().trim()
        val vLower = version.lowercase().trim()

        if (pref == SettingsConstants.Quality.AUTO || pref.isBlank()) {
            val res = parseQualityResolution(quality)
            return if (res > 0) -res else 0
        }

        if (qLower.contains(pref) || vLower.contains(pref)) {
            return -10000
        }

        val res = parseQualityResolution(quality)
        return if (res > 0) 10000 - res else 10000
    }

    fun selectVideoMatchingQuality(
        videos: List<ExtractedVideo>,
        preferredQuality: String,
    ): ExtractedVideo? {
        if (videos.isEmpty()) return null
        if (videos.size == 1) return videos.first()

        val pref = preferredQuality.lowercase().trim()
        if (pref == SettingsConstants.Quality.AUTO || pref.isBlank()) {
            return videos.maxByOrNull { parseQualityResolution(it.quality) } ?: videos.first()
        }

        val exactMatch = videos.find {
            it.quality.contains(pref, ignoreCase = true) ||
                it.version.contains(pref, ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch

        val preferredRes = parseQualityResolution(pref)
        if (preferredRes > 0) {
            val belowOrEqual = videos.filter { parseQualityResolution(it.quality) in 1..preferredRes }
            if (belowOrEqual.isNotEmpty()) {
                return belowOrEqual.maxByOrNull { parseQualityResolution(it.quality) }
            }
            val above = videos.filter { parseQualityResolution(it.quality) > preferredRes }
            if (above.isNotEmpty()) {
                return above.minByOrNull { parseQualityResolution(it.quality) }
            }
        }

        return videos.maxByOrNull { parseQualityResolution(it.quality) } ?: videos.first()
    }
}
