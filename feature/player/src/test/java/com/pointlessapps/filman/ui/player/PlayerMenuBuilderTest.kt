package com.pointlessapps.filman.ui.player

import com.pointlessapps.filman.data.api.VideoUrlResolver
import com.pointlessapps.filman.data.api.extractors.ExtractedVideo
import com.pointlessapps.filman.data.local.SettingsManager
import com.pointlessapps.filman.data.model.SourcePriorityConfig
import com.pointlessapps.filman.ui.components.FilmanOverlayMenuItem
import com.pointlessapps.filman.ui.components.FilmanOverlayClickScope
import com.pointlessapps.filman.ui.base.FilmanEvent
import com.pointlessapps.filman.player.lektor.engine.SystemVoiceList
import com.pointlessapps.filman.player.lektor.model.LektorVoice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMenuBuilderTest {
    @Test
    fun `voice only option dispatches preference even when lektor is disabled`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<FilmanEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(PlayerState(), resolver, null) { events.add(it) }
        val lektor = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor" }
        lektor.items.filterIsInstance<FilmanOverlayMenuItem.Option>()
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
        val option = items.filterIsInstance<FilmanOverlayMenuItem.Option>().single()
        assertTrue(option.isSelected)
        option.onClick()
        assertEquals(listOf(id), selected)
        items.filterIsInstance<FilmanOverlayMenuItem.Button>()
            .first { it.id == "lektor_system_preview" }
            .onClick(object : FilmanOverlayClickScope { override fun popBack() = Unit })
        assertEquals(listOf("pl-PL-x-local"), previews)
    }

    @Test
    fun `system voice is selectable without a model download`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }

        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<FilmanEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            PlayerState(
                isLektorEnabled = true,
                lektorVoiceId = "system",
                systemVoices = SystemVoiceList(loading = false, names = listOf("pl-PL-local")),
            ),
            resolver,
            null,
        ) { events.add(it) }
        val lektor = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor" }
        val option = lektor.items.filterIsInstance<FilmanOverlayMenuItem.Option>()
            .first { it.id == "lektor_voice_system" }

        assertTrue(option.isSelected)
        assertEquals(null, option.value)
        assertTrue(lektor.items.none { it.id.startsWith("lektor_model_") || it.id == "header_lektor_model" })
        val systemIndex = lektor.items.indexOfFirst { it.id == "lektor_voice_system" }
        val namedIndex = lektor.items.indexOfFirst { it.id == "lektor_voice_system:pl-PL-local" }
        val localMenu = lektor.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor_local_models" }
        val localIndex = lektor.items.indexOfFirst { it.id == "menu_lektor_local_models" }
        assertTrue(systemIndex >= 0 && namedIndex > systemIndex && localIndex > namedIndex)
        assertTrue(localMenu.items.any { it.id == "lektor_voice_meski" })
        option.onClick()
        assertEquals("system", (events.last() as PlayerEvent.SelectLektorVoice).voiceId)
    }

    @Test
    fun `neural voices are selectable without model downloads`() {
        val settings = mockk<SettingsManager> {
            every { sourcesPriorityFlow } returns MutableStateFlow(emptyList())
        }
        val resolver = mockk<VideoUrlResolver> {
            every { settingsManager } returns settings
        }
        val events = mutableListOf<FilmanEvent>()
        val menu = PlayerMenuBuilder.buildOverlayMenuData(
            PlayerState(isLektorEnabled = true, lektorVoiceId = "edge_zofia"),
            resolver,
            null,
        ) { events.add(it) }
        val lektor = menu.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor" }
        val onlineMenu = lektor.items.filterIsInstance<FilmanOverlayMenuItem.NestedMenu>()
            .first { it.id == "menu_lektor_online_models" }
        val options = onlineMenu.items.filterIsInstance<FilmanOverlayMenuItem.Option>()
        val marek = options.first { it.id == "lektor_voice_edge_marek" }
        val zofia = options.first { it.id == "lektor_voice_edge_zofia" }
        assertTrue(!marek.isSelected && zofia.isSelected)
        assertEquals(null, zofia.value)
        assertTrue(onlineMenu.items.any { it.id == "lektor_online_notice" })
        assertTrue(onlineMenu.items.none { it.id.startsWith("lektor_model_") || it.id == "header_lektor_model" })
        marek.onClick()
        assertEquals("edge_marek", (events.last() as PlayerEvent.SelectLektorVoice).voiceId)
    }

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
