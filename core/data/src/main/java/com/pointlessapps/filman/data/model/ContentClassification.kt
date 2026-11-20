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
        it.equals("japan", ignoreCase = true) || it.equals("japonia", ignoreCase = true)
    }
    val isPoland = lang == "pl" || countries.any {
        it.equals("poland", ignoreCase = true) || it.equals("polska", ignoreCase = true)
    }
    val isAnimation = 16 in genreIds
    val isFamilyOrKids = 10751 in genreIds || 10762 in genreIds

    return when {
        isFromKidsSection || (isAnimation && isFamilyOrKids) -> ContentCategory.KIDS_FAMILY
        isAnimation && isJapan -> ContentCategory.ANIME
        isPoland -> ContentCategory.NATIVE_DOMESTIC
        99 in genreIds -> ContentCategory.DOCUMENTARY
        lang == "en" || countries.any {
            it.contains("United States", ignoreCase = true) ||
                it.contains("United Kingdom", ignoreCase = true) ||
                it.contains("Stany Zjednoczone", ignoreCase = true) ||
                it.contains("Wielka Brytania", ignoreCase = true)
        } -> ContentCategory.FOREIGN_MAINSTREAM
        else -> ContentCategory.FOREIGN_WORLD
    }
}
