package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.api.OpenSubtitleCandidate
import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleMenuTest {
    @Test
    fun `OpenSubtitles candidates offer individual downloads`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val candidate = OpenSubtitleCandidate(42, "release.srt", "pl", "release", 10, 8.0, false, false)
        val events = mutableListOf<FilmanEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            PlayerState(openSubtitleCandidates = listOf(candidate)), resolver, "menu_open_subtitle_results",
        ) { events.add(it) }
        val subtitles = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_subtitles" }
        val results = subtitles.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_open_subtitle_results" }
        results.items.filterIsInstance<FilmanOverlayMenuItem.Option>().single().onClick()
        assertEquals(candidate, (events.last() as PlayerEvent.DownloadOpenSubtitle).candidate)
    }
}
