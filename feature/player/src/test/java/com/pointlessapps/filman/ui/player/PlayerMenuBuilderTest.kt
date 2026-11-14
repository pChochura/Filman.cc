package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMenuBuilderTest {
    @Test
    fun `CDA-HD sources follow configured priority before TMDB`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(
                listOf(SourcePriorityConfig("cda_hd", true), SourcePriorityConfig("tmdb", true)),
            )
        }
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val state = PlayerState(
            alternativeSources = listOf(
                ExtractedVideo(url = "https://example.com/tmdb", sourceWebsite = "tmdb"),
                ExtractedVideo(url = "https://example.com/cda", sourceWebsite = "cda-hd.cc"),
            ),
        )
        val menu = PlayerMenuBuilder.buildOverlayMenuData(state, resolver, null) {}
        val sources = menu.items.first() as FilmanOverlayMenuItem.NestedMenu
        assertEquals(
            listOf("header_cda-hd.cc", "header_tmdb"),
            sources.items.filterIsInstance<FilmanOverlayMenuItem.Header>().map { it.id },
        )
    }
}
