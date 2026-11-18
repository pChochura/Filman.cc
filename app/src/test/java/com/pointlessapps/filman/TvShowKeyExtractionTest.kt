package com.pointlessapps.filman

import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.getTvShowKey
import com.pointlessapps.filman.data.model.normalizeTvShowKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvShowKeyExtractionTest {
    @Test
    fun testNormalizeTvShowKey() {
        assertEquals("show_1396", "show_1396".normalizeTvShowKey())
        assertEquals("show_1396", "show_1396_1_2".normalizeTvShowKey())
        assertEquals("show_1396", "1396".normalizeTvShowKey())
        assertNull("https://filman.cc/serial-online/36/breaking-bad".normalizeTvShowKey())
        assertNull("/serial-online/36/breaking-bad".normalizeTvShowKey())
        assertNull(null.normalizeTvShowKey())
    }

    @Test
    fun testTvShowEpisodeWithSeriesUrl() {
        val episode1 =
            MovieItem(
                url = "show_1396_1_1",
                titlePl = "Breaking Bad - s01e01",
                posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
                seriesUrl = "show_1396",
                seasonNumber = 1,
                episodeNumber = 1,
                isTvShow = true,
            )

        val episode2 =
            MovieItem(
                url = "show_1396_1_2",
                titlePl = "Breaking Bad - s01e02",
                posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
                seriesUrl = "show_1396",
                seasonNumber = 1,
                episodeNumber = 2,
                isTvShow = true,
            )

        assertEquals("show_1396", episode1.getTvShowKey())
        assertEquals("show_1396", episode2.getTvShowKey())
        assertEquals(episode1.getTvShowKey(), episode2.getTvShowKey())
    }

    @Test
    fun testTvShowEpisodeWithoutSeriesUrlDerivesKeyFromTmdbUrl() {
        val episode1 =
            MovieItem(
                url = "show_66732_1_1",
                titlePl = "Stranger Things - s01e01",
                posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
                seasonNumber = 1,
                episodeNumber = 1,
                isTvShow = true,
            )

        val episode2 =
            MovieItem(
                url = "show_66732_1_2",
                titlePl = "Stranger Things - s01e02",
                posterUrl = "https://image.tmdb.org/t/p/w500/poster.jpg",
                seasonNumber = 1,
                episodeNumber = 2,
                isTvShow = true,
            )

        assertEquals("show_66732", episode1.getTvShowKey())
        assertEquals("show_66732", episode2.getTvShowKey())
        assertEquals(episode1.getTvShowKey(), episode2.getTvShowKey())
    }

    @Test
    fun testMovieAndLegacyUrlsReturnNullKey() {
        val movie =
            MovieItem(
                url = "movie_27205",
                titlePl = "Inception",
                posterUrl = "https://image.tmdb.org/t/p/w500/inception.jpg",
            )
        val legacyShow =
            MovieItem(
                url = "https://filman.cc/serial-online/36/breaking-bad/s01e01",
                titlePl = "Breaking Bad",
                posterUrl = "",
                seriesUrl = "https://filman.cc/serial-online/36/breaking-bad",
                isTvShow = true,
            )

        assertNull(movie.getTvShowKey())
        assertNull(legacyShow.getTvShowKey())
    }

    @Test
    fun testDifferentTvShowsHaveDifferentKeys() {
        val breakingBad =
            MovieItem(
                url = "show_1396_1_1",
                titlePl = "Breaking Bad",
                posterUrl = "",
                seriesUrl = "show_1396",
                seasonNumber = 1,
                episodeNumber = 1,
                isTvShow = true,
            )

        val betterCallSaul =
            MovieItem(
                url = "show_60059_1_1",
                titlePl = "Better Call Saul",
                posterUrl = "",
                seriesUrl = "show_60059",
                seasonNumber = 1,
                episodeNumber = 1,
                isTvShow = true,
            )

        assertEquals("show_1396", breakingBad.getTvShowKey())
        assertEquals("show_60059", betterCallSaul.getTvShowKey())
        assert(breakingBad.getTvShowKey() != betterCallSaul.getTvShowKey())
    }
}
