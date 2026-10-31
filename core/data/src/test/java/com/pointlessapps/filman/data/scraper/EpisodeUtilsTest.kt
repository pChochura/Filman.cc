package com.pointlessapps.filman.data.scraper

import com.pointlessapps.filman.data.model.EpisodeLink
import com.pointlessapps.filman.data.model.Season
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeUtilsTest {

    @Test
    fun testExtractEpisodeNumberFromVariousFormats() {
        // [s01e02] and variations
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("[s01e02] Odcinek 2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("[s1e2]"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("[S01E02] Winter is Coming"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("(s01e02)"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("s01e02"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("s01.e02"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("s01-e02"))

        // NxNN
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("1x02"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("[01x02] Title"))

        // Odcinek / Episode
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Odcinek 2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Odcinek 02"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Odc. 2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("odc 2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("odc.02"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Episode 2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Ep. 2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("ep 2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("episode[2]"))

        // Lone E
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("E02"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("E 2"))

        // Leading index
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("2. Title"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("02 - Title"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("2) Title"))

        // URL fallback when title does not have episode number
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Winter is Coming", "https://filman.cc/serial-online/tytul/123-odcinek-2"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Winter is Coming", "https://zaluknij.cc/serial-online/tytul-s01e02/456"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Winter is Coming", "https://ekino-tv.pl/watch/show/123+season[1]+episode[2]+"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Winter is Coming", "/serial/123/2.html"))
        assertEquals(2, EpisodeUtils.extractEpisodeNumber("Winter is Coming", "/serial/123/odcinek-2"))
    }

    @Test
    fun testExtractSeasonNumber() {
        assertEquals(1, EpisodeUtils.extractSeasonNumber("Sezon 1"))
        assertEquals(1, EpisodeUtils.extractSeasonNumber("Sezon 01"))
        assertEquals(2, EpisodeUtils.extractSeasonNumber("Season 2"))
        assertEquals(1, EpisodeUtils.extractSeasonNumber("[s01] Sezon 1"))
        assertEquals(1, EpisodeUtils.extractSeasonNumber("s01"))
        assertEquals(0, EpisodeUtils.extractSeasonNumber("Odcinki specjalne"))
        assertEquals(0, EpisodeUtils.extractSeasonNumber("Specials"))
        assertEquals(2, EpisodeUtils.extractSeasonNumber("Unknown", "https://site.com/show+season[2]+"))
    }

    @Test
    fun testCleanEpisodeTitle() {
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("[s01e02] Odcinek 2", 2))
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("[s01e02] - Odcinek 2", 2))
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("(s01e02) Odcinek 2", 2))
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("s01e02 - Odcinek 2", 2))
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("[s01e02]", 2))
        assertEquals("Winter Is Coming", EpisodeUtils.cleanEpisodeTitle("[s01e02] Winter Is Coming", 2))
        assertEquals("Winter Is Coming", EpisodeUtils.cleanEpisodeTitle("[s01e02] - Winter Is Coming", 2))
        assertEquals("Winter Is Coming", EpisodeUtils.cleanEpisodeTitle("1x02 - Winter Is Coming", 2))
        assertEquals("Winter Is Coming", EpisodeUtils.cleanEpisodeTitle("Winter Is Coming [s01e02]", 2))
        assertEquals("Winter Is Coming", EpisodeUtils.cleanEpisodeTitle("Winter Is Coming (s01e02)", 2))
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("Odcinek 2", 2))
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("Odcinek 02", 2))
        assertEquals("Odcinek 2", EpisodeUtils.cleanEpisodeTitle("odc. 2", 2))
        assertEquals("Odcinek 2 - Winter Is Coming", EpisodeUtils.cleanEpisodeTitle("Odcinek 2 - Winter Is Coming", 2))
    }

    @Test
    fun testDeduplicateEpisodesWithBracketedAndCleanDuplicates() {
        val rawEpisodes = listOf(
            EpisodeLink(title = "Odcinek 1", url = "https://filman.cc/serial/1-odcinek-1"),
            EpisodeLink(title = "Odcinek 2", url = "https://filman.cc/serial/2-odcinek-2"),
            EpisodeLink(title = "[s01e01] Odcinek 1", url = "https://zaluknij.cc/serial/s01e01/101"),
            EpisodeLink(title = "[s01e02] Odcinek 2", url = "https://zaluknij.cc/serial/s01e02/102"),
            EpisodeLink(title = "[s01e03] Odcinek 3", url = "https://zaluknij.cc/serial/s01e03/103"),
        )

        val deduplicated = EpisodeUtils.deduplicateEpisodes(rawEpisodes)

        assertEquals(3, deduplicated.size)
        assertEquals("Odcinek 1", deduplicated[0].title)
        assertEquals("https://filman.cc/serial/1-odcinek-1", deduplicated[0].url)

        assertEquals("Odcinek 2", deduplicated[1].title)
        assertEquals("https://filman.cc/serial/2-odcinek-2", deduplicated[1].url)

        assertEquals("Odcinek 3", deduplicated[2].title)
        assertEquals("https://zaluknij.cc/serial/s01e03/103", deduplicated[2].url)
    }

    @Test
    fun testDeduplicateEpisodesPrefersDescriptiveTitle() {
        val rawEpisodes = listOf(
            EpisodeLink(title = "Odcinek 1", url = "https://filman.cc/1"),
            EpisodeLink(title = "[s01e01] Nadchodzi zima", url = "https://zaluknij.cc/1"),
        )

        val deduplicated = EpisodeUtils.deduplicateEpisodes(rawEpisodes)

        assertEquals(1, deduplicated.size)
        assertEquals("Nadchodzi zima", deduplicated[0].title)
        assertEquals("https://filman.cc/1", deduplicated[0].url)
    }

    @Test
    fun testMergeSeasons() {
        val currentSeasons = listOf(
            Season(
                name = "Sezon 1",
                episodes = listOf(
                    EpisodeLink(title = "Odcinek 1", url = "https://filman.cc/1"),
                    EpisodeLink(title = "Odcinek 2", url = "https://filman.cc/2"),
                ),
            ),
        )

        val newSeasons = listOf(
            Season(
                name = "[s01] Sezon 1",
                episodes = listOf(
                    EpisodeLink(title = "[s01e01] Odcinek 1", url = "https://zaluknij.cc/1"),
                    EpisodeLink(title = "[s01e02] Odcinek 2", url = "https://zaluknij.cc/2"),
                    EpisodeLink(title = "[s01e03] Odcinek 3", url = "https://zaluknij.cc/3"),
                ),
            ),
            Season(
                name = "Sezon 2",
                episodes = listOf(
                    EpisodeLink(title = "[s02e01] Odcinek 1", url = "https://zaluknij.cc/201"),
                ),
            ),
        )

        val merged = EpisodeUtils.mergeSeasons(currentSeasons, newSeasons)

        assertEquals(2, merged?.size)

        // Season 1
        val season1 = merged!![0]
        assertEquals("Sezon 1", season1.name)
        assertEquals(3, season1.episodes.size)
        assertEquals("Odcinek 1", season1.episodes[0].title)
        assertEquals("https://filman.cc/1", season1.episodes[0].url)
        assertEquals("Odcinek 2", season1.episodes[1].title)
        assertEquals("https://filman.cc/2", season1.episodes[1].url)
        assertEquals("Odcinek 3", season1.episodes[2].title)
        assertEquals("https://zaluknij.cc/3", season1.episodes[2].url)

        // Season 2
        val season2 = merged[1]
        assertEquals("Sezon 2", season2.name)
        assertEquals(1, season2.episodes.size)
        assertEquals("Odcinek 1", season2.episodes[0].title)
        assertEquals("https://zaluknij.cc/201", season2.episodes[0].url)
    }
}
