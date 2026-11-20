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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamApiResolverTest {
    @Test
    fun `existing embeds do not bypass the stream API`() = runTest {
        val repository = mockk<TiwiRepository>()
        val settings = mockk<SettingsManager> {
            io.mockk.every { preferredQualityFlow } returns MutableStateFlow(SettingsConstants.Quality.AUTO)
        }
        coEvery { repository.getLinks("movie_42", false, any()) } returns LinksResponse(
            status = "READY",
            links = listOf(VideoLink(url = "https://example.test/api.mp4", sourceWebsite = "stream_api")),
        )
        val resolver = VideoUrlResolver(repository, settings)
        val media = DetailedMedia(
            baseItem = MovieItem(url = "movie_42", titlePl = "Example", posterUrl = ""),
            embeds = listOf(EmbedLink("https://example.test/legacy", "Legacy", sourceWebsite = "tmdb")),
        )

        assertEquals(listOf("https://example.test/api.mp4"), resolver.resolveAll("movie_42", media).map { it.url })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `partial streams are playable without waiting for completion`() = runTest {
        val repository = mockk<TiwiRepository>()
        val settings = mockk<SettingsManager> {
            io.mockk.every { preferredQualityFlow } returns MutableStateFlow(SettingsConstants.Quality.AUTO)
        }
        coEvery { repository.getLinks("movie_42", false, any()) } returnsMany listOf(
            LinksResponse(status = "PENDING"),
            LinksResponse(
                status = "PARTIAL",
                links = listOf(VideoLink(url = "https://example.test/first.mp4", sourceWebsite = "stream_api")),
            ),
        )
        val resolver = VideoUrlResolver(repository, settings)
        val media = DetailedMedia(baseItem = MovieItem(url = "movie_42", titlePl = "Example", posterUrl = ""))

        assertEquals(listOf("https://example.test/first.mp4"), resolver.resolveAll("movie_42", media).map { it.url })
        assertEquals(250L, currentTime)
    }

    @Test
    fun `prefetch wakes up repository and resolves sources for media`() = runTest {
        val repository = mockk<TiwiRepository>(relaxed = true)
        val settings = mockk<SettingsManager> {
            io.mockk.every { preferredQualityFlow } returns MutableStateFlow(SettingsConstants.Quality.AUTO)
        }
        val media = DetailedMedia(baseItem = MovieItem(url = "show_123_1_2", titlePl = "Episode 2", posterUrl = "", isTvShow = true))
        coEvery { repository.getMediaDetails("show_123_1_2") } returns media
        coEvery { repository.getLinks("show_123_1_2", true, any()) } returns LinksResponse(
            status = "READY",
            links = listOf(VideoLink(url = "https://example.test/ep2.mp4", sourceWebsite = "stream_api")),
        )
        val resolver = VideoUrlResolver(repository, settings)
        val prefetched = resolver.prefetch("show_123_1_2")

        io.mockk.verify { repository.wakeUp() }
        assertEquals(listOf("https://example.test/ep2.mp4"), prefetched.map { it.url })
    }

    @Test
    fun `prefetch handles repository failure gracefully without throwing`() = runTest {
        val repository = mockk<TiwiRepository>(relaxed = true)
        val settings = mockk<SettingsManager> {
            io.mockk.every { preferredQualityFlow } returns MutableStateFlow(SettingsConstants.Quality.AUTO)
        }
        coEvery { repository.getMediaDetails("show_123_1_2") } throws RuntimeException("Server error")
        val resolver = VideoUrlResolver(repository, settings)
        val prefetched = resolver.prefetch("show_123_1_2")

        assertEquals(emptyList<com.pointlessapps.filman.data.api.extractors.ExtractedVideo>(), prefetched)
    }
}
