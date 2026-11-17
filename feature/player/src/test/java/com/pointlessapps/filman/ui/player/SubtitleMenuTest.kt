package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.SubtitleCandidate
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleMenuTest {
    @Test
    fun `Subtitle candidates offer individual downloads`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }
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
        val events = mutableListOf<FilmanEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            PlayerState(subtitleCandidates = listOf(openCandidate, wyzieCandidate)),
            resolver,
            "menu_subtitle_results",
        ) { events.add(it) }
        val subtitles = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitles" }
        val results = subtitles.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitle_results" }

        val options = results.items.filterIsInstance<FilmanOverlayMenuItem.Option>()
        assertEquals(2, options.size)

        options[0].onClick()
        assertEquals(openCandidate, (events.last() as PlayerEvent.DownloadSubtitle).candidate)

        options[1].onClick()
        assertEquals(wyzieCandidate, (events.last() as PlayerEvent.DownloadSubtitle).candidate)
    }
}
