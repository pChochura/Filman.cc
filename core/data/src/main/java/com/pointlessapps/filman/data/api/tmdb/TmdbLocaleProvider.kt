package com.pointlessapps.filman.data.api.tmdb

import java.util.Locale

class TmdbLocaleProvider(
    private val localeSupplier: () -> Locale = { Locale.getDefault() },
) {
    fun languageTag(): String {
        val tag = localeSupplier().toLanguageTag()
        return tag.takeIf { it.isNotBlank() && it != "und" } ?: "en-US"
    }

    fun languageCode(): String {
        val code = localeSupplier().language
        return code.takeIf { it.isNotBlank() && it != "und" } ?: "en"
    }

    fun countryCode(): String {
        val country = localeSupplier().country.uppercase(Locale.ROOT)
        return country.takeIf { it.length == 2 } ?: "US"
    }

    fun kidsMovieCertification(country: String = countryCode()): Pair<String, String> =
        when (country.uppercase(Locale.ROOT)) {
            "US" -> "US" to "PG"
            "PL" -> "PL" to "12"
            "GB" -> "GB" to "PG"
            "DE" -> "DE" to "12"
            "FR" -> "FR" to "12"
            "ES" -> "ES" to "12"
            "IT" -> "IT" to "T"
            "CA" -> "CA" to "PG"
            "AU" -> "AU" to "PG"
            "BR" -> "BR" to "12"
            "NL" -> "NL" to "12"
            "SE" -> "SE" to "11"
            "NO" -> "NO" to "12"
            "DK" -> "DK" to "11"
            "FI" -> "FI" to "K-12"
            "CZ" -> "CZ" to "12"
            "SK" -> "SK" to "12"
            "HU" -> "HU" to "12"
            "PT" -> "PT" to "M/12"
            "IN" -> "IN" to "U"
            else -> "US" to "PG"
        }

    fun mapMovieGenreIds(genreId: Int): String =
        when (genreId) {
            10759 -> "28|12"
            10765 -> "878|14"
            10768 -> "10752"
            10762 -> "10751|16"
            else -> genreId.toString()
        }

    fun mapTvGenreIds(genreId: Int): String =
        when (genreId) {
            28, 12 -> "10759"
            878, 14 -> "10765"
            10752 -> "10768"
            else -> genreId.toString()
        }

    fun mapMovieGenreIds(genreIds: List<Int>): String =
        when (genreIds.size) {
            0 -> ""
            1 -> mapMovieGenreIds(genreIds.single())
            else -> genreIds
                .map { mapMovieGenreIds(it).substringBefore('|') }
                .distinct()
                .joinToString(",")
        }

    fun mapTvGenreIds(genreIds: List<Int>): String =
        genreIds
            .map(::mapTvGenreIds)
            .distinct()
            .joinToString(",")

    fun matchesGenre(
        itemGenreIds: List<Int>,
        selectedGenreId: Int,
        isTvShow: Boolean,
    ): Boolean {
        if (selectedGenreId in itemGenreIds) return true
        val mapped = if (isTvShow) mapTvGenreIds(selectedGenreId) else mapMovieGenreIds(selectedGenreId)
        return mapped
            .split('|')
            .mapNotNull { it.toIntOrNull() }
            .any { it in itemGenreIds }
    }
}
