package com.pointlessapps.filman.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

enum class ContentCategory(val key: String) {
    KIDS_FAMILY("kids"),
    ANIME("anime"),
    NATIVE_DOMESTIC("native_pl"),
    FOREIGN_MAINSTREAM("foreign_en"),
    FOREIGN_WORLD("foreign_world"),
    DOCUMENTARY("documentary");

    companion object {
        fun fromKey(key: String?): ContentCategory =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: FOREIGN_MAINSTREAM
    }
}

enum class AudioPreferenceMode(val value: String) {
    DUBBING_PREFERRED("dubbing"),
    LEKTOR_PREFERRED("lektor"),
    ORIGINAL_SUBTITLES("original_subtitles"),
    ORIGINAL_ONLY("original_only");

    companion object {
        fun fromValue(value: String?): AudioPreferenceMode =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: LEKTOR_PREFERRED
    }
}

@Serializable
@Immutable
data class ContentRule(
    val audioMode: String = AudioPreferenceMode.LEKTOR_PREFERRED.value,
    val subtitlesEnabled: Boolean = false,
    val subtitleLanguage: String = "pl",
) {
    val resolvedAudioMode: AudioPreferenceMode
        get() = AudioPreferenceMode.fromValue(audioMode)
}

@Serializable
@Immutable
data class ContentRulesPreferences(
    val rules: Map<String, ContentRule> = DEFAULT_CONTENT_RULES,
) {
    fun getRule(category: ContentCategory): ContentRule =
        rules[category.key] ?: DEFAULT_CONTENT_RULES[category.key] ?: ContentRule()

    companion object {
        val DEFAULT_CONTENT_RULES: Map<String, ContentRule> = mapOf(
            ContentCategory.KIDS_FAMILY.key to ContentRule(
                audioMode = AudioPreferenceMode.DUBBING_PREFERRED.value,
                subtitlesEnabled = false,
                subtitleLanguage = "pl",
            ),
            ContentCategory.ANIME.key to ContentRule(
                audioMode = AudioPreferenceMode.ORIGINAL_SUBTITLES.value,
                subtitlesEnabled = true,
                subtitleLanguage = "pl",
            ),
            ContentCategory.NATIVE_DOMESTIC.key to ContentRule(
                audioMode = AudioPreferenceMode.ORIGINAL_ONLY.value,
                subtitlesEnabled = false,
                subtitleLanguage = "pl",
            ),
            ContentCategory.FOREIGN_MAINSTREAM.key to ContentRule(
                audioMode = AudioPreferenceMode.LEKTOR_PREFERRED.value,
                subtitlesEnabled = false,
                subtitleLanguage = "pl",
            ),
            ContentCategory.FOREIGN_WORLD.key to ContentRule(
                audioMode = AudioPreferenceMode.ORIGINAL_SUBTITLES.value,
                subtitlesEnabled = true,
                subtitleLanguage = "pl",
            ),
            ContentCategory.DOCUMENTARY.key to ContentRule(
                audioMode = AudioPreferenceMode.LEKTOR_PREFERRED.value,
                subtitlesEnabled = false,
                subtitleLanguage = "pl",
            ),
        )
    }
}

fun classifyContent(
    genreIds: List<Int>,
    originalLanguage: String?,
    countries: List<String>,
    isFromKidsSection: Boolean = false,
): ContentCategory {
    val lang = originalLanguage?.lowercase()?.trim()
    val isJapan = lang == "ja" || countries.any {
        val c = it.lowercase().trim()
        c == "japan" || c == "japonia" || c == "jp"
    }
    val isPoland = lang == "pl" || lang == "pol" || countries.any {
        val c = it.lowercase().trim()
        c == "pl" || c == "pol" || c == "poland" || c == "polska"
    }
    val isAnimation = 16 in genreIds
    val isFamilyOrKids = 10751 in genreIds || 10762 in genreIds

    return when {
        isPoland -> ContentCategory.NATIVE_DOMESTIC
        isFromKidsSection || (isAnimation && isFamilyOrKids) -> ContentCategory.KIDS_FAMILY
        isAnimation && isJapan -> ContentCategory.ANIME
        99 in genreIds -> ContentCategory.DOCUMENTARY
        lang == "en" || countries.any {
            val c = it.lowercase().trim()
            c.contains("united states") ||
                c.contains("united kingdom") ||
                c.contains("stany zjednoczone") ||
                c.contains("wielka brytania") ||
                c == "us" || c == "gb" || c == "uk"
        } -> ContentCategory.FOREIGN_MAINSTREAM
        else -> ContentCategory.FOREIGN_WORLD
    }
}

fun isOriginallyPolish(
    originalLanguage: String?,
    countries: List<String>,
    sourceVersion: String? = null,
    sourceFilename: String? = null,
    allSources: List<com.pointlessapps.filman.data.api.extractors.ExtractedVideo> = emptyList(),
): Boolean {
    val lang = originalLanguage?.lowercase()?.trim()
    if (lang == "pl" || lang == "pol") return true
    if (countries.any {
        val c = it.lowercase().trim()
        c == "pl" || c == "pol" || c == "poland" || c == "polska"
    }) return true

    fun checkSource(ver: String?, file: String?): Boolean {
        val v = ver?.lowercase()?.trim().orEmpty()
        val f = file?.lowercase()?.trim().orEmpty()
        val isExplicitForeign = v.contains("dub") || v.contains("lektor") || v.contains("lek") ||
            v.contains("napisy") || v.contains("sub") || f.contains("dubbing") || f.contains("pldub") ||
            f.contains("lektor") || f.contains("pllek") || f.contains("plsub")
        val isExplicitPolish = (v == "pl" || v == "org" || v.contains("oryg") || f.contains(".pl.") || f.contains("[pl]")) && !isExplicitForeign
        return isExplicitPolish
    }

    if (lang.isNullOrBlank() && countries.isEmpty()) {
        if (checkSource(sourceVersion, sourceFilename)) return true
        if (allSources.isNotEmpty() && allSources.any { checkSource(it.version, it.filename) } &&
            allSources.none {
                val v = it.version.lowercase().trim()
                v.contains("dub") || v.contains("lektor") || v.contains("lek")
            }) {
            return true
        }
    }
    return false
}

fun hasPolishSpokenAudio(
    isPolishProduction: Boolean,
    sourceVersion: String?,
    sourceFilename: String? = null,
): Boolean {
    val v = sourceVersion?.lowercase()?.trim().orEmpty()
    val f = sourceFilename?.lowercase()?.trim().orEmpty()

    val isForeignTrack = v.contains("eng") || v.contains("en") || f.contains(".en.") || f.contains("[eng]")
    val isDubbed = (v.contains("dub") || f.contains("dubbing") || f.contains("pldub")) && !isForeignTrack
    val isLektor = v.contains("lektor") || v.contains("lek") || f.contains("lektor") || f.contains("pllek")
    if (isDubbed || isLektor) return true

    if (isPolishProduction) {
        return !isForeignTrack
    }

    val isExplicitPlAudio = (v == "pl" || v == "pol" || f.contains(".pl.") || f.contains("[pl]")) &&
        !v.contains("napisy") && !v.contains("sub") && !f.contains("plsub") && !f.contains("napisy")
    return isExplicitPlAudio
}

fun resolveShouldEnableLektor(
    isPolishProduction: Boolean,
    hasPolishSpokenAudio: Boolean,
    contentRule: ContentRule,
    isLektorSettingEnabled: Boolean,
): Boolean {
    if (!isLektorSettingEnabled) return false
    if (isPolishProduction) return false
    if (hasPolishSpokenAudio) return false
    if (contentRule.resolvedAudioMode == AudioPreferenceMode.ORIGINAL_ONLY) return false

    return true
}

fun resolveSubtitlesEnabled(
    isPolishProduction: Boolean,
    hasPolishSpokenAudio: Boolean,
    contentRule: ContentRule,
    shouldEnableLektor: Boolean,
    savedSubtitlesEnabled: Boolean? = null,
): Boolean {
    if (savedSubtitlesEnabled != null) {
        return savedSubtitlesEnabled
    }
    if (shouldEnableLektor) {
        return true
    }
    return when (contentRule.resolvedAudioMode) {
        AudioPreferenceMode.ORIGINAL_SUBTITLES -> true
        AudioPreferenceMode.ORIGINAL_ONLY -> false
        AudioPreferenceMode.DUBBING_PREFERRED,
        AudioPreferenceMode.LEKTOR_PREFERRED -> contentRule.subtitlesEnabled
    }
}
