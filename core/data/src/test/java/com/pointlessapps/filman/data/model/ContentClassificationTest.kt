package com.pointlessapps.filman.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentClassificationTest {

    @Test
    fun `classifyContent accurately categorizes Kids, Anime, Polish, Foreign, and Documentaries`() {
        // Kids / Family
        assertEquals(
            ContentCategory.KIDS_FAMILY,
            classifyContent(
                genreIds = listOf(16, 10751),
                originalLanguage = "en",
                countries = listOf("United States"),
            ),
        )
        assertEquals(
            ContentCategory.KIDS_FAMILY,
            classifyContent(
                genreIds = listOf(16),
                originalLanguage = "en",
                countries = emptyList(),
                isFromKidsSection = true,
            ),
        )

        // Anime (Animation + Japan)
        assertEquals(
            ContentCategory.ANIME,
            classifyContent(
                genreIds = listOf(16),
                originalLanguage = "ja",
                countries = listOf("Japan"),
            ),
        )

        // Native Polish
        assertEquals(
            ContentCategory.NATIVE_DOMESTIC,
            classifyContent(
                genreIds = listOf(18),
                originalLanguage = "pl",
                countries = listOf("Poland"),
            ),
        )
        assertEquals(
            ContentCategory.NATIVE_DOMESTIC,
            classifyContent(
                genreIds = listOf(16, 10751),
                originalLanguage = "pl",
                countries = listOf("PL"),
            ),
        )
        assertEquals(
            ContentCategory.NATIVE_DOMESTIC,
            classifyContent(
                genreIds = listOf(16),
                originalLanguage = "pl",
                countries = emptyList(),
                isFromKidsSection = true,
            ),
        )

        // Documentary
        assertEquals(
            ContentCategory.DOCUMENTARY,
            classifyContent(
                genreIds = listOf(99),
                originalLanguage = "en",
                countries = listOf("United Kingdom"),
            ),
        )

        // Foreign mainstream (English)
        assertEquals(
            ContentCategory.FOREIGN_MAINSTREAM,
            classifyContent(
                genreIds = listOf(28, 12),
                originalLanguage = "en",
                countries = listOf("United States"),
            ),
        )

        // Foreign world (e.g. Korean)
        assertEquals(
            ContentCategory.FOREIGN_WORLD,
            classifyContent(
                genreIds = listOf(18, 53),
                originalLanguage = "ko",
                countries = listOf("South Korea"),
            ),
        )
    }

    @Test
    fun `MovieItem getMediaKey returns show canonicalKey for episodes and movie canonicalKey for movies`() {
        val movie = MovieItem(url = "movie_603", titlePl = "Matrix", posterUrl = "")
        val episode = MovieItem(
            url = "show_1396_1_1",
            seriesUrl = "show_1396",
            seasonNumber = 1,
            episodeNumber = 1,
            titlePl = "Breaking Bad",
            posterUrl = "",
            isTvShow = true,
        )

        assertEquals("movie_603", movie.getMediaKey())
        assertEquals("show_1396", episode.getMediaKey())
    }

    @Test
    fun `default rules contain expected values for each category`() {
        val prefs = ContentRulesPreferences()

        val kidsRule = prefs.getRule(ContentCategory.KIDS_FAMILY)
        assertEquals(AudioPreferenceMode.DUBBING_PREFERRED, kidsRule.resolvedAudioMode)
        assertEquals(false, kidsRule.subtitlesEnabled)

        val animeRule = prefs.getRule(ContentCategory.ANIME)
        assertEquals(AudioPreferenceMode.ORIGINAL_SUBTITLES, animeRule.resolvedAudioMode)
        assertEquals(true, animeRule.subtitlesEnabled)

        val polishRule = prefs.getRule(ContentCategory.NATIVE_DOMESTIC)
        assertEquals(AudioPreferenceMode.ORIGINAL_ONLY, polishRule.resolvedAudioMode)
        assertEquals(false, polishRule.subtitlesEnabled)

        val foreignRule = prefs.getRule(ContentCategory.FOREIGN_MAINSTREAM)
        assertEquals(AudioPreferenceMode.LEKTOR_PREFERRED, foreignRule.resolvedAudioMode)
        assertEquals(false, foreignRule.subtitlesEnabled)
    }

    @Test
    fun `isOriginallyPolish identifies Polish titles by TMDB data and sources`() {
        org.junit.Assert.assertTrue(isOriginallyPolish(originalLanguage = "pl", countries = emptyList()))
        org.junit.Assert.assertTrue(isOriginallyPolish(originalLanguage = "en", countries = listOf("PL")))
        org.junit.Assert.assertTrue(isOriginallyPolish(originalLanguage = null, countries = listOf("Polska")))
        org.junit.Assert.assertTrue(
            isOriginallyPolish(
                originalLanguage = null,
                countries = emptyList(),
                sourceVersion = "PL",
                sourceFilename = "Chlopi.2023.PL.1080p.mkv",
            ),
        )
        org.junit.Assert.assertFalse(
            isOriginallyPolish(
                originalLanguage = "en",
                countries = listOf("United States"),
                sourceVersion = "Lektor",
            ),
        )
    }

    @Test
    fun `hasPolishSpokenAudio correctly differentiates Polish dialogue from foreign tracks`() {
        // Polish production with native audio
        org.junit.Assert.assertTrue(hasPolishSpokenAudio(isPolishProduction = true, sourceVersion = "PL"))
        org.junit.Assert.assertTrue(hasPolishSpokenAudio(isPolishProduction = true, sourceVersion = "Oryginal"))
        org.junit.Assert.assertFalse(hasPolishSpokenAudio(isPolishProduction = true, sourceVersion = "ENG Dub"))

        // Foreign production with dubbed or lektor audio
        org.junit.Assert.assertTrue(hasPolishSpokenAudio(isPolishProduction = false, sourceVersion = "Lektor PL"))
        org.junit.Assert.assertTrue(hasPolishSpokenAudio(isPolishProduction = false, sourceVersion = "Dubbing"))
        org.junit.Assert.assertFalse(hasPolishSpokenAudio(isPolishProduction = false, sourceVersion = "Napisy"))
        org.junit.Assert.assertFalse(hasPolishSpokenAudio(isPolishProduction = false, sourceVersion = ""))
    }

    @Test
    fun `resolveShouldEnableLektor respects Polish titles and audio mode preferences`() {
        val lektorRule = ContentRule(audioMode = AudioPreferenceMode.LEKTOR_PREFERRED.value)
        val originalOnlyRule = ContentRule(audioMode = AudioPreferenceMode.ORIGINAL_ONLY.value)

        // Polish production: NEVER enable voiceover
        org.junit.Assert.assertFalse(
            resolveShouldEnableLektor(
                isPolishProduction = true,
                hasPolishSpokenAudio = true,
                contentRule = originalOnlyRule,
                isLektorSettingEnabled = true,
            ),
        )

        // Foreign production with lektor or dubbing: NEVER enable voiceover
        org.junit.Assert.assertFalse(
            resolveShouldEnableLektor(
                isPolishProduction = false,
                hasPolishSpokenAudio = true,
                contentRule = lektorRule,
                isLektorSettingEnabled = true,
            ),
        )

        // Foreign production with napisy/foreign audio: enable voiceover when preferred
        org.junit.Assert.assertTrue(
            resolveShouldEnableLektor(
                isPolishProduction = false,
                hasPolishSpokenAudio = false,
                contentRule = lektorRule,
                isLektorSettingEnabled = true,
            ),
        )

        // Foreign production when user explicitly chose ORIGINAL_ONLY: do NOT enable
        org.junit.Assert.assertFalse(
            resolveShouldEnableLektor(
                isPolishProduction = false,
                hasPolishSpokenAudio = false,
                contentRule = originalOnlyRule,
                isLektorSettingEnabled = true,
            ),
        )
    }
}
