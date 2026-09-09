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
        assertEquals(
            "/serial-online/36/breaking-bad",
            "https://filman.cc/serial-online/36/breaking-bad".normalizeTvShowKey(),
        )
        assertEquals(
            "/serial-online/36/breaking-bad",
            "http://filman.cc/serial-online/36/breaking-bad/".normalizeTvShowKey(),
        )
        assertEquals(
            "/serial-online/36/breaking-bad",
            "https://filman.cc/serial-online/36/breaking-bad?ref=abc#top".normalizeTvShowKey(),
        )
        assertNull(null.normalizeTvShowKey())
    }

    @Test
    fun testTvShowEpisodeWithSeriesUrl() {
        val episode1 = MovieItem(
            url = "https://filman.cc/serial-online/36/breaking-bad/s01e01",
            titlePl = "Breaking Bad - s01e01",
            posterUrl = "https://filman.cc/poster.jpg",
            seriesUrl = "https://filman.cc/serial-online/36/breaking-bad",
            seasonNumber = 1,
            episodeNumber = 1,
        )

        val episode2 = MovieItem(
            url = "https://filman.cc/serial-online/36/breaking-bad/s01e02",
            titlePl = "Breaking Bad - s01e02",
            posterUrl = "https://filman.cc/poster.jpg",
            seriesUrl = "https://filman.cc/serial-online/36/breaking-bad",
            seasonNumber = 1,
            episodeNumber = 2,
        )

        assertEquals("/serial-online/36/breaking-bad", episode1.getTvShowKey())
        assertEquals("/serial-online/36/breaking-bad", episode2.getTvShowKey())
        assertEquals(episode1.getTvShowKey(), episode2.getTvShowKey())
    }

    @Test
    fun testTvShowEpisodeWithoutSeriesUrlFallsBackToTitle() {
        val episode1 = MovieItem(
            url = "https://ekino-tv.pl/watch/show/123/ep1",
            titlePl = "Stranger Things - s01e01",
            posterUrl = "https://ekino-tv.pl/poster.jpg",
            seasonNumber = 1,
            episodeNumber = 1,
        )

        val episode2 = MovieItem(
            url = "https://ekino-tv.pl/watch/show/123/ep2",
            titlePl = "Stranger Things - s01e02",
            posterUrl = "https://ekino-tv.pl/poster.jpg",
            seasonNumber = 1,
            episodeNumber = 2,
        )

        assertEquals("title:stranger things", episode1.getTvShowKey())
        assertEquals("title:stranger things", episode2.getTvShowKey())
        assertEquals(episode1.getTvShowKey(), episode2.getTvShowKey())
    }

    @Test
    fun testMovieReturnsNullKey() {
        val movie = MovieItem(
            url = "https://filman.cc/film-online/12345/inception",
            titlePl = "Inception",
            posterUrl = "https://filman.cc/inception.jpg",
        )

        assertNull(movie.getTvShowKey())
    }

    @Test
    fun testDifferentTvShowsHaveDifferentKeys() {
        val breakingBad = MovieItem(
            url = "https://filman.cc/serial-online/36/breaking-bad/s01e01",
            titlePl = "Breaking Bad",
            posterUrl = "https://filman.cc/bb.jpg",
            seriesUrl = "https://filman.cc/serial-online/36/breaking-bad",
            seasonNumber = 1,
            episodeNumber = 1,
        )

        val betterCallSaul = MovieItem(
            url = "https://filman.cc/serial-online/500/better-call-saul/s01e01",
            titlePl = "Better Call Saul",
            posterUrl = "https://filman.cc/bcs.jpg",
            seriesUrl = "https://filman.cc/serial-online/500/better-call-saul",
            seasonNumber = 1,
            episodeNumber = 1,
        )

        assertEquals("/serial-online/36/breaking-bad", breakingBad.getTvShowKey())
        assertEquals("/serial-online/500/better-call-saul", betterCallSaul.getTvShowKey())
        assert(breakingBad.getTvShowKey() != betterCallSaul.getTvShowKey())
    }
}
