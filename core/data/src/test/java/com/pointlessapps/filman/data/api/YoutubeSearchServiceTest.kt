package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MovieItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class YoutubeSearchServiceTest {

    private val service = YoutubeSearchService()

    @Test
    fun `service can be instantiated`() {
        assertNotNull(service)
    }

    @Test
    fun `searchFullMovie returns null for empty titles`() = runTest {
        val emptyMovie = DetailedMedia(
            baseItem = MovieItem(
                url = "movie:empty",
                titlePl = "",
                titleEn = "",
                posterUrl = "",
            )
        )
        val result = service.searchFullMovie(emptyMovie)
        assertNull(result)
    }
}
