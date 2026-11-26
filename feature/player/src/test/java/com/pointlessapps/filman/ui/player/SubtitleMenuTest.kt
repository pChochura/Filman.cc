package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.Subtitle
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubtitleMenuTest {
    @Test
    fun `Subtitle candidates offer individual downloads`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val openCandidate = SubtitleCandidate(
            id = "open_42",
            name = "release.srt",
            language = "pl",
            release = "release",
            provider = "OpenSubtitles",
            downloadCount = 10,
            rating = 8.0,
            openSubtitleFileId = 42,
        )
        val wyzieCandidate = SubtitleCandidate(
            id = "wyzie_0_12345",
            name = "Polish",
            language = "pl",
            provider = "Wyzie",
            directUrl = "https://sub.wyzie.io/c/123",
        )
        val events = mutableListOf<TiwiEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            PlayerState(subtitleCandidates = listOf(openCandidate, wyzieCandidate)),
            resolver,
            "menu_subtitle_results",
        ) { events.add(it) }
        val subtitles = menu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitles" }
        val results = subtitles.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitle_results" }

        val options = results.items.filterIsInstance<TiwiOverlayMenuItem.Option>()
        assertEquals(2, options.size)

        options[0].onClick()
        assertEquals(openCandidate, (events.last() as PlayerEvent.DownloadSubtitle).candidate)

        options[1].onClick()
        assertEquals(wyzieCandidate, (events.last() as PlayerEvent.DownloadSubtitle).candidate)
    }

    @Test
    fun `Subtitles menu with voice-over and duplicate open subtitles has strictly unique item IDs`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val sub1 = Subtitle(url = "https://subtitles.com/pl.srt", label = "Polish (Voice-over)")
        val sub2 = Subtitle(url = "https://subtitles.com/pl.srt", label = "Polish (Voice-over)")
        val embeddedSub = Subtitle(url = "https://subtitles.com/embedded.vtt", label = "English Embedded")
        val state = PlayerState(
            isLektorEnabled = true,
            selectedSubtitleUrl = "https://subtitles.com/pl.srt",
            openSubtitles = listOf(sub1, sub2),
            subtitles = listOf(embeddedSub, embeddedSub),
        )
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            state,
            resolver,
            "menu_subtitles",
        ) {}
        val subtitlesMenu = menu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitles" }

        val ids = subtitlesMenu.items.map { it.id }
        assertEquals(ids.size, ids.distinct().size)

        val syncMenu = subtitlesMenu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .firstOrNull { it.id == "menu_subtitle_sync" }
        assertNotNull(syncMenu)
        val syncIds = syncMenu!!.items.map { it.id }
        assertEquals(syncIds.size, syncIds.distinct().size)
    }

    @Test
    fun `Subtitle candidates list deduplicates candidates with identical IDs`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val dupCandidate1 = SubtitleCandidate(
            id = "dup_1",
            name = "duplicate.srt",
            language = "pl",
            provider = "OpenSubtitles",
        )
        val dupCandidate2 = SubtitleCandidate(
            id = "dup_1",
            name = "duplicate.srt",
            language = "pl",
            provider = "OpenSubtitles",
        )
        val state = PlayerState(
            subtitleCandidates = listOf(dupCandidate1, dupCandidate2),
        )
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            state,
            resolver,
            "menu_subtitle_results",
        ) {}
        val subtitles = menu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitles" }
        val results = subtitles.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitle_results" }

        val ids = results.items.map { it.id }
        assertEquals(1, ids.size)
        assertEquals(ids.size, ids.distinct().size)
    }
}
