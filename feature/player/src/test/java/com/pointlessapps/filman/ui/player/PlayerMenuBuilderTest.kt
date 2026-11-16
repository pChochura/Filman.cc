package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.base.FilmanEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `failed sources remain selectable for manual retry`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val first = ExtractedVideo(url = "https://example.test/first", serverName = "First", sourceWebsite = "stream_api")
        val second = ExtractedVideo(url = "https://example.test/second", serverName = "Second", sourceWebsite = "stream_api")
        val state = PlayerState(
            videoUrl = second.url,
            alternativeSources = listOf(first, second),
            failedUrls = setOf(first.url, second.url),
        )
        val events = mutableListOf<FilmanEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(state, resolver, null) { events.add(it) }
        val sources = menu.items.first() as FilmanOverlayMenuItem.NestedMenu
        val options = sources.items.filterIsInstance<FilmanOverlayMenuItem.Option>()

        assertEquals(2, options.size)
        options.first().onClick()
        assertTrue(events.last() is PlayerEvent.ChangeVideoSource)
        assertEquals(first, (events.last() as PlayerEvent.ChangeVideoSource).source)
    }
}
