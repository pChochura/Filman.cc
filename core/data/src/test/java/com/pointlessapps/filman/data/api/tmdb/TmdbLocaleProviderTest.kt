package com.pointlessapps.filman.data.api.tmdb

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TmdbLocaleProviderTest {
    @Test
    fun `languageTag, languageCode, and countryCode reflect device locale with fallbacks`() {
        val polish = TmdbLocaleProvider { Locale.Builder().setLanguage("pl").setRegion("PL").build() }
        assertEquals("pl-PL", polish.languageTag())
        assertEquals("pl", polish.languageCode())
        assertEquals("PL", polish.countryCode())

        val blank = TmdbLocaleProvider { Locale.ROOT }
        assertEquals("en-US", blank.languageTag())
        assertEquals("en", blank.languageCode())
        assertEquals("US", blank.countryCode())
    }

    @Test
    fun `kidsMovieCertification maps supported countries and falls back to US PG`() {
        val provider = TmdbLocaleProvider { Locale.US }
        assertEquals("PL" to "12", provider.kidsMovieCertification("PL"))
        assertEquals("US" to "PG", provider.kidsMovieCertification("US"))
        assertEquals("IT" to "T", provider.kidsMovieCertification("IT"))
        assertEquals("US" to "PG", provider.kidsMovieCertification("ZZ"))
    }

    @Test
    fun `genre mappings convert between Movie and TV TMDB genre IDs`() {
        val provider = TmdbLocaleProvider { Locale.US }

        assertEquals("28|12", provider.mapMovieGenreIds(10759))
        assertEquals("878|14", provider.mapMovieGenreIds(10765))
        assertEquals("10752", provider.mapMovieGenreIds(10768))
        assertEquals("10751|16", provider.mapMovieGenreIds(10762))
        assertEquals("35", provider.mapMovieGenreIds(35))

        assertEquals("10759", provider.mapTvGenreIds(28))
        assertEquals("10759", provider.mapTvGenreIds(12))
        assertEquals("10765", provider.mapTvGenreIds(878))
        assertEquals("10765", provider.mapTvGenreIds(14))
        assertEquals("10768", provider.mapTvGenreIds(10752))
        assertEquals("35", provider.mapTvGenreIds(35))
    }
}
