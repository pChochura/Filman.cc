package com.pointlessapps.filman.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRefTest {
    @Test
    fun `parse parses movie canonical key`() {
        val ref = MediaRef.parse("movie_27205")
        assertNotNull(ref)
        assertEquals(MediaType.MOVIE, ref!!.type)
        assertEquals(27205, ref.tmdbId)
        assertNull(ref.season)
        assertNull(ref.episode)
        assertFalse(ref.isTvShow)
        assertFalse(ref.isEpisode)
        assertEquals("movie_27205", ref.canonicalKey)
        assertEquals(ref, ref.parentRef)
    }

    @Test
    fun `parse parses show canonical key`() {
        val ref = MediaRef.parse("show_1396")
        assertNotNull(ref)
        assertEquals(MediaType.TV, ref!!.type)
        assertEquals(1396, ref.tmdbId)
        assertNull(ref.season)
        assertNull(ref.episode)
        assertTrue(ref.isTvShow)
        assertFalse(ref.isEpisode)
        assertEquals("show_1396", ref.canonicalKey)
        assertEquals(ref, ref.parentRef)
    }

    @Test
    fun `parse parses episode canonical key and exposes parentRef`() {
        val ref = MediaRef.parse("show_1396_1_2")
        assertNotNull(ref)
        assertEquals(MediaType.TV, ref!!.type)
        assertEquals(1396, ref.tmdbId)
        assertEquals(1, ref.season)
        assertEquals(2, ref.episode)
        assertTrue(ref.isTvShow)
        assertTrue(ref.isEpisode)
        assertEquals("show_1396_1_2", ref.canonicalKey)
        assertEquals(MediaRef(MediaType.TV, 1396), ref.parentRef)
        assertEquals("show_1396", ref.parentRef.canonicalKey)
    }

    @Test
    fun `parse strips search prefix and uses isTvShowHint for bare numeric IDs`() {
        val movieRef = MediaRef.parse("search_movie_550")
        assertEquals(MediaRef(MediaType.MOVIE, 550), movieRef)

        val hintedTvRef = MediaRef.parse("1396", isTvShowHint = true)
        assertEquals(MediaRef(MediaType.TV, 1396), hintedTvRef)

        val defaultMovieRef = MediaRef.parse("550")
        assertEquals(MediaRef(MediaType.MOVIE, 550), defaultMovieRef)
    }

    @Test
    fun `parse rejects legacy URLs and malformed inputs`() {
        assertNull(MediaRef.parse(null))
        assertNull(MediaRef.parse(""))
        assertNull(MediaRef.parse("   "))
        assertNull(MediaRef.parse("https://filman.cc/film-online/12345/inception"))
        assertNull(MediaRef.parse("http://example.com/123"))
        assertNull(MediaRef.parse("/serial-online/36/breaking-bad"))
        assertNull(MediaRef.parse("movie_0"))
        assertNull(MediaRef.parse("movie_-5"))
        assertNull(MediaRef.parse("movie_abc"))
    }

    @Test
    fun `MovieItem sanitizeToTmdb canonicalizes TMDB entries and drops legacy URLs`() {
        val legacy = MovieItem(
            url = "https://filman.cc/film-online/12345/inception",
            titlePl = "Incepcja",
            posterUrl = "",
        )
        assertNull(legacy.sanitizeToTmdb())

        val rawEpisode = MovieItem(
            url = "1396_2_3",
            titlePl = "Breaking Bad",
            posterUrl = "",
            isTvShow = true,
        )
        val sanitized = rawEpisode.sanitizeToTmdb()
        assertNotNull(sanitized)
        assertEquals("show_1396_2_3", sanitized!!.url)
        assertEquals("show_1396", sanitized.seriesUrl)
        assertEquals(2, sanitized.seasonNumber)
        assertEquals(3, sanitized.episodeNumber)
        assertTrue(sanitized.isTvShow)
        assertEquals(DetailsRequest.Url("show_1396_2_3", isTvShow = true), sanitized.detailsRequest)
    }
}
