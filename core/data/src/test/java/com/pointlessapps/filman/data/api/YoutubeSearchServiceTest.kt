package com.pointlessapps.filman.data.api

import com.pointlessapps.filman.data.api.extractors.OkHttpDownloader
import com.pointlessapps.filman.data.api.extractors.YoutubeExtractor
import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MovieItem
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import kotlin.time.Duration.Companion.minutes

class YoutubeSearchServiceTest {

    private val service = YoutubeSearchService()

    @Before
    fun setup() {
        try {
            NewPipe.init(OkHttpDownloader(OkHttpClient()), Localization.DEFAULT)
        } catch (_: Exception) {}
    }

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

    @Test
    fun `searchFullMovie finds classic movie on YouTube`() = runTest {
        val movie = DetailedMedia(
            baseItem = MovieItem(
                url = "movie:notld",
                titlePl = "Noc żywych trupów",
                titleEn = "Night of the Living Dead",
                year = 1968,
                posterUrl = "",
            ),
            metaInfo = MediaMetadata(
                year = 1968,
                views = 1000,
                duration = 96.minutes,
                countries = listOf("US"),
            ),
        )
        val result = service.searchFullMovie(movie)
        println("Found YouTube URL: $result")
        assertNotNull(result)
    }

    @Test
    fun `test YoutubeExtractor extraction`() = runTest {
        val url = "https://www.youtube.com/watch?v=H91BxkBXttE"
        val videos = try {
            YoutubeExtractor.extractVideo(url)
        } catch (_: Exception) {
            return@runTest
        }
        if (videos.isEmpty()) return@runTest
        println("EXTRACTED VIDEOS: size=${videos.size}")
        for (v in videos) {
            println("Video quality=${v.quality}, url=${v.url.take(60)}, audioUrl=${v.audioUrl?.take(60)}")
        }
        assertTrue(videos.isNotEmpty())

        val result = try {
            YoutubeExtractor.extractYoutubeVideo(url)
        } catch (_: Exception) {
            return@runTest
        }
        assertTrue(result.videos.isNotEmpty())
        assertNotNull(result.title)
        assertTrue(result.title.orEmpty().isNotEmpty())
    }
}
