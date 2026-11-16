package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.dto.LinksResponse
import com.pointlessapps.filman.data.api.dto.VideoLink
import com.pointlessapps.filman.data.local.SettingsConstants
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.EmbedLink
import com.pointlessapps.filman.data.model.MovieItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamApiResolverTest {
    @Test
    fun `existing embeds do not bypass the stream API`() = runTest {
        val repository = mockk<FilmanRepository>()
        val settings = mockk<SettingsManager> {
            io.mockk.every { preferredQualityFlow } returns MutableStateFlow(SettingsConstants.Quality.AUTO)
        }
        coEvery { repository.getLinks("movie_42", false, any()) } returns LinksResponse(
            status = "READY",
            links = listOf(VideoLink(url = "https://example.test/api.mp4", sourceWebsite = "stream_api")),
        )
        val resolver = VideoUrlResolver(repository, settings, mockk(), mockk())
        val media = DetailedMedia(
            baseItem = MovieItem(url = "movie_42", titlePl = "Example", posterUrl = ""),
            embeds = listOf(EmbedLink("https://example.test/legacy", "Legacy", sourceWebsite = "tmdb")),
        )

        assertEquals(listOf("https://example.test/api.mp4"), resolver.resolveAll("movie_42", media).map { it.url })
    }
}
