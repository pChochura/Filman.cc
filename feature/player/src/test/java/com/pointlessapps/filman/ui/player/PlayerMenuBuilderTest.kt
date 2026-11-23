package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.ui.components.TiwiOverlayMenuItem
import com.pointlessapps.filman.ui.components.TiwiOverlayClickScope
import com.pointlessapps.filman.ui.base.TiwiEvent
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMenuBuilderTest {
    @Test
    fun `voice only option dispatches preference even when lektor is disabled`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<TiwiEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(PlayerState(), resolver, null) { events.add(it) }
        val lektor = menu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor" }
        lektor.items.filterIsInstance<TiwiOverlayMenuItem.Option>()
            .single { it.id == "lektor_hide_subtitles" }.onClick()
        assertEquals(PlayerEvent.SetLektorHideSubtitles(true), events.single())
    }

    @Test
    fun `missing selected system voice warns instead of previewing a different voice`() {
        val items = systemVoiceMenuItems(
            SystemVoiceList(loading = false, names = listOf("available")),
            LektorVoice.systemVoiceId("missing"),
            {},
            {},
            {},
        )
        assertTrue(items.any { it.id == "lektor_system_selected_missing" })
        assertTrue(items.none { it.id == "lektor_system_preview" })
    }

    @Test
    fun `system voices can be selected and previewed`() {
        val id = LektorVoice.systemVoiceId("pl-PL-x-local")
        val selected = mutableListOf<String>()
        val previews = mutableListOf<String?>()
        val items = systemVoiceMenuItems(
            SystemVoiceList(loading = false, names = listOf("pl-PL-x-local")),
            id,
            selected::add,
            previews::add,
            {},
        )
        val option = items.filterIsInstance<TiwiOverlayMenuItem.Option>().single()
        assertTrue(option.isSelected)
        option.onClick()
        assertEquals(listOf(id), selected)
        items.filterIsInstance<TiwiOverlayMenuItem.Button>()
            .first { it.id == "lektor_system_preview" }
            .onClick(object : TiwiOverlayClickScope { override fun popBack() = Unit })
        assertEquals(listOf("pl-PL-x-local"), previews)
    }

    @Test
    fun `system voice is selectable without a model download`() {
        val settings = mockk<SettingsManager>(relaxed = true)

        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<TiwiEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            PlayerState(
                isLektorEnabled = true,
                lektorVoiceId = "system",
                systemVoices = SystemVoiceList(loading = false, names = listOf("pl-PL-local")),
            ),
            resolver,
            null,
        ) { events.add(it) }
        val lektor = menu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor" }
        val otherVoices = lektor.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor_other_voices" }
        val option = otherVoices.items.filterIsInstance<TiwiOverlayMenuItem.Option>()
            .first { it.id == "lektor_voice_system" }

        assertTrue(option.isSelected)
        assertEquals(null, option.value)
        assertTrue(otherVoices.items.none { it.id.startsWith("lektor_model_") || it.id == "header_lektor_model" })
        val systemIndex = otherVoices.items.indexOfFirst { it.id == "lektor_voice_system" }
        val namedIndex = otherVoices.items.indexOfFirst { it.id == "lektor_voice_system:pl-PL-local" }
        val localMenu = otherVoices.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor_local_models" }
        val localIndex = otherVoices.items.indexOfFirst { it.id == "menu_lektor_local_models" }
        assertTrue(systemIndex >= 0 && namedIndex > systemIndex && localIndex > namedIndex)
        assertTrue(localMenu.items.any { it.id == "lektor_voice_meski" })
        option.onClick()
        assertEquals("system", (events.last() as PlayerEvent.SelectLektorVoice).voiceId)
    }

    @Test
    fun `neural voices are selectable without model downloads`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<TiwiEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            PlayerState(isLektorEnabled = true, lektorVoiceId = "edge_zofia"),
            resolver,
            null,
        ) { events.add(it) }
        val lektor = menu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor" }
        val options = lektor.items.filterIsInstance<TiwiOverlayMenuItem.Option>()
        val marek = options.first { it.id == "lektor_voice_edge_marek" }
        val zofia = options.first { it.id == "lektor_voice_edge_zofia" }
        assertTrue(!marek.isSelected && zofia.isSelected)
        assertEquals(null, zofia.value)
        marek.onClick()
        assertEquals("edge_marek", (events.last() as PlayerEvent.SelectLektorVoice).voiceId)
    }

    @Test
    fun `stream API sources group under Stream API header`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val state = PlayerState(
            alternativeSources = listOf(
                ExtractedVideo(url = "https://example.com/1", serverName = "S1", sourceWebsite = "stream_api"),
                ExtractedVideo(url = "https://example.com/2", serverName = "S2", sourceWebsite = "other.cc"),
            ),
        )
        val menu = PlayerMenuBuilder.buildOverlayMenuData(state, resolver, null) {}
        val sources = menu.items.first() as TiwiOverlayMenuItem.NestedMenu
        assertEquals(
            listOf("header_stream_api", "header_other.cc"),
            sources.items.filterIsInstance<TiwiOverlayMenuItem.Header>().map { it.id },
        )
    }

    @Test
    fun `failed sources remain selectable for manual retry`() {
        val settings = mockk<SettingsManager>(relaxed = true)
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
        val events = mutableListOf<TiwiEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(state, resolver, null) { events.add(it) }
        val sources = menu.items.first() as TiwiOverlayMenuItem.NestedMenu
        val options = sources.items.filterIsInstance<TiwiOverlayMenuItem.Option>()

        assertEquals(2, options.size)
        options.first().onClick()
        assertTrue(events.last() is PlayerEvent.ChangeVideoSource)
        assertEquals(first, (events.last() as PlayerEvent.ChangeVideoSource).source)
    }

    @Test
    fun `test voice button in lektor menu dispatches PlayerEvent TestVoice`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<TiwiEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(PlayerState(), resolver, null) { events.add(it) }
        val lektor = menu.items.filterIsInstance<TiwiOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor" }
        val testVoiceBtn = lektor.items.filterIsInstance<TiwiOverlayMenuItem.Button>()
            .single { it.id == "lektor_test_voice" }
        testVoiceBtn.onClick(object : TiwiOverlayClickScope {
            override fun popBack() = Unit
        })
        assertEquals(PlayerEvent.TestVoice, events.last())
    }

    @Test
    fun `sources menu shows loader while isFetchingMoreSources is true`() {
        val settings = mockk<SettingsManager>(relaxed = true)
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val state = PlayerState(
            isFetchingMoreSources = true,
            alternativeSources = listOf(
                ExtractedVideo(url = "https://example.com/1", serverName = "S1", sourceWebsite = "stream_api"),
            ),
        )
        val menu = PlayerMenuBuilder.buildOverlayMenuData(state, resolver, null) {}
        val sources = menu.items.first() as TiwiOverlayMenuItem.NestedMenu
        val loader = sources.items.filterIsInstance<TiwiOverlayMenuItem.Button>()
            .find { it.id == "sources_loading_indicator" }
        assertNotNull(loader)
        assertTrue(loader!!.isLoading)
    }
}
