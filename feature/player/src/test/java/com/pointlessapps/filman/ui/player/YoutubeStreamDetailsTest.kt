package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.model.DetailedMedia
import com.pointlessapps.filman.data.model.MediaMetadata
import com.pointlessapps.filman.data.model.MovieItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class YoutubeStreamDetailsTest {

    @Test
    fun `movie item retains trailer flag and metadata`() {
        val item = MovieItem(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            titlePl = "Gladiator II",
            titleEn = "Gladiator II",
            posterUrl = "https://example.com/poster.jpg",
            description = "Epic historical action film.",
            isTrailer = true,
        )

        assertTrue(item.isTrailer)
        assertEquals("Gladiator II", item.titlePl)
        assertEquals("Epic historical action film.", item.description)
    }

    @Test
    fun `trailer title formatting appends trailer suffix only when not already present`() {
        fun formatTitle(item: MovieItem, trailerSuffix: String): String {
            val baseTitle = item.titlePl.ifBlank { null } ?: item.titleEn.orEmpty()
            return buildString {
                append(baseTitle)
                if (item.isTrailer) {
                    if (!baseTitle.contains("trailer", ignoreCase = true) &&
                        !baseTitle.contains("zwiastun", ignoreCase = true)
                    ) {
                        append(" - $trailerSuffix")
                    }
                } else {
                    item.episodeTitle?.let { append(" - $it") }
                }
            }
        }

        val plainTrailer = MovieItem(
            url = "https://www.youtube.com/watch?v=123",
            titlePl = "Inception",
            isTrailer = true,
            posterUrl = "",
        )
        assertEquals("Inception - Zwiastun", formatTitle(plainTrailer, "Zwiastun"))
        assertEquals("Inception - Trailer", formatTitle(plainTrailer, "Trailer"))

        val alreadyMarkedTrailer = MovieItem(
            url = "https://www.youtube.com/watch?v=123",
            titlePl = "Inception - Official Trailer",
            isTrailer = true,
            posterUrl = "",
        )
        assertEquals("Inception - Official Trailer", formatTitle(alreadyMarkedTrailer, "Zwiastun"))

        val fullMovie = MovieItem(
            url = "https://www.youtube.com/watch?v=456",
            titlePl = "Night of the Living Dead",
            isTrailer = false,
            posterUrl = "",
        )
        assertEquals("Night of the Living Dead", formatTitle(fullMovie, "Zwiastun"))
    }

    @Test
    fun `full youtube movie keeps description and duration`() {
        val details = DetailedMedia(
            baseItem = MovieItem(
                url = "https://www.youtube.com/watch?v=fullmovie",
                titlePl = "Night of the Living Dead",
                description = "Classic horror movie.",
                isTrailer = false,
                posterUrl = "",
            ),
            metaInfo = MediaMetadata(
                duration = 96.minutes,
            ),
        )

        assertFalse(details.baseItem.isTrailer)
        assertEquals("Night of the Living Dead", details.baseItem.titlePl)
        assertEquals("Classic horror movie.", details.baseItem.description)
        assertEquals(96.minutes, details.metaInfo?.duration)
    }
}
