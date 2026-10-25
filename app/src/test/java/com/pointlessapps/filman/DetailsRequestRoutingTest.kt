package com.pointlessapps.filman

import com.pointlessapps.filman.data.model.DetailsRequest
import com.pointlessapps.filman.data.model.MovieItem
import com.pointlessapps.filman.data.model.getTvShowKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsRequestRoutingTest {

    @Test
    fun testDetailsRequestUrlDefaults() {
        val tvRequest = DetailsRequest.Url("show_1396")
        assertTrue(tvRequest.isTvShow)

        val movieRequest = DetailsRequest.Url("movie_1396")
        assertFalse(movieRequest.isTvShow)

        val rawRequest = DetailsRequest.Url("1396")
        assertFalse(rawRequest.isTvShow)

        val explicitTvRequest = DetailsRequest.Url("1396", isTvShow = true)
        assertTrue(explicitTvRequest.isTvShow)
    }

    @Test
    fun testTvShowKeyWithPrefixedSeriesUrl() {
        val episode = MovieItem(
            url = "show_1396_1_1",
            titlePl = "Breaking Bad - Pilot",
            posterUrl = "",
            seriesUrl = "show_1396",
            seasonNumber = 1,
            episodeNumber = 1,
            isTvShow = true,
        )
        assertEquals("show_1396", episode.getTvShowKey())
    }
}
